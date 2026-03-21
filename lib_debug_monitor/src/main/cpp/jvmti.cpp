#include <jni.h>
#include <jvmti.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <atomic>
#include <algorithm>
#include <cstdint>
#include <unordered_map>

#define LOG_TAG "JVMTI_AGENT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct MethodEvent {
    enum class Kind {
        METHOD_ENTRY,
        METHOD_EXIT,
        FIELD_MODIFICATION
    };

    Kind kind = Kind::METHOD_ENTRY;
    std::string className;
    int64_t classUniqueId = 0;
    std::string methodName;
    std::string methodDesc;
    std::string fieldName;
    char fieldSignatureType = 0;
};

struct MethodInterest {
    jclass baseClassGlobal = nullptr;
    jobject methodRefGlobal = nullptr;
    std::string baseClassName;
    std::string methodName;
    std::string methodDesc;
    bool includeSubclasses = false;
};

struct WatchedField {
    jclass ownerClassGlobal = nullptr;
    jfieldID fieldId = nullptr;
};

static JavaVM* g_vm = nullptr;
static jvmtiEnv* g_jvmti = nullptr;
static jclass g_debugStackMotionClass = nullptr;
static jmethodID g_onMethodVisitMethod = nullptr;
static jmethodID g_onVariableVisitMethod = nullptr;

static std::mutex g_interestMutex;
static std::vector<MethodInterest> g_interestedMethods;
static std::mutex g_watchFieldMutex;
static std::vector<WatchedField> g_watchedFields;

static std::mutex g_queueMutex;
static std::condition_variable g_queueCv;
static std::deque<MethodEvent> g_eventQueue;
static std::atomic<bool> g_consumerShouldRun(false);
static std::thread g_consumerThread;
static std::atomic<int64_t> g_nextClassUniqueId(1);
static std::atomic<uint64_t> g_forwardedEventCount(0);
static std::atomic<bool> g_fieldModificationEnabled(false);
static std::atomic<bool> g_bridgeMissingLogged(false);
static std::mutex g_classCacheMutex;
static std::unordered_map<std::string, jclass> g_classGlobalCache;
static std::unordered_map<std::string, bool> g_assignableCache;
static std::atomic<bool> g_canAccessLocalVariables(false);
static std::atomic<bool> g_releaseInProgress(false);

static void startConsumerThread();
static int64_t getOrAssignClassUniqueId(jclass clazz);

static std::string NormalizeClassName(const char* signature) {
    if (signature == nullptr) {
        return "";
    }
    std::string result(signature);
    if (!result.empty()) {
        if (result[0] == 'L' && result.back() == ';') {
            result = result.substr(1, result.size() - 2);
        } else if (result[0] == '[' && result.size() > 2 && result[1] == 'L' && result.back() == ';') {
            std::string component = result.substr(2, result.size() - 3);
            std::replace(component.begin(), component.end(), '/', '.');
            return "[L" + component + ";";
        }
    }
    std::replace(result.begin(), result.end(), '/', '.');
    return result;
}

static bool cacheDebugStackMotionClass(JNIEnv* env, jclass bridgeClass) {
    if (env == nullptr || bridgeClass == nullptr) {
        return false;
    }
    if (g_debugStackMotionClass == nullptr) {
        g_debugStackMotionClass = reinterpret_cast<jclass>(env->NewGlobalRef(bridgeClass));
        if (g_debugStackMotionClass == nullptr) {
            LOGE("Failed to create global ref for DebugStackMotion class");
            return false;
        }
    }
    g_onMethodVisitMethod = env->GetStaticMethodID(g_debugStackMotionClass,
                                                    "onMethodVisit",
                                                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");
    g_onVariableVisitMethod = env->GetStaticMethodID(g_debugStackMotionClass,
                                                      "onVariableVisit",
                                                      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Z)V");
    if (g_onMethodVisitMethod == nullptr || g_onVariableVisitMethod == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        LOGE("Failed to cache DebugStackMotion methods");
        return false;
    }
    g_bridgeMissingLogged.store(false, std::memory_order_relaxed);
    return true;
}

static bool ensureDebugStackMotionClass(JNIEnv* env) {
    if (g_debugStackMotionClass != nullptr &&
        g_onMethodVisitMethod != nullptr &&
        g_onVariableVisitMethod != nullptr) {
        return true;
    }
    bool expected = false;
    if (g_bridgeMissingLogged.compare_exchange_strong(expected, true)) {
        LOGE("DebugStackMotion callback bridge is not prepared");
    }
    return false;
}

static void releaseDebugStackMotionClass(JNIEnv* env) {
    if (g_debugStackMotionClass != nullptr) {
        env->DeleteGlobalRef(g_debugStackMotionClass);
        g_debugStackMotionClass = nullptr;
    }
    g_onMethodVisitMethod = nullptr;
    g_onVariableVisitMethod = nullptr;
    g_bridgeMissingLogged.store(false, std::memory_order_relaxed);
}

static void clearClassGlobalCache(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(g_classCacheMutex);
    if (env != nullptr) {
        for (auto& item : g_classGlobalCache) {
            if (item.second != nullptr) {
                env->DeleteGlobalRef(item.second);
            }
        }
    }
    g_classGlobalCache.clear();
    g_assignableCache.clear();
}

static jclass findClassGlobal(JNIEnv* env, const std::string& className) {
    if (env == nullptr || className.empty()) {
        return nullptr;
    }
    {
        std::lock_guard<std::mutex> lock(g_classCacheMutex);
        auto it = g_classGlobalCache.find(className);
        if (it != g_classGlobalCache.end()) {
            return it->second;
        }
    }

    jclass classClass = env->FindClass("java/lang/Class");
    if (classClass == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return nullptr;
    }
    jmethodID forName = env->GetStaticMethodID(classClass, "forName",
                                               "(Ljava/lang/String;)Ljava/lang/Class;");
    if (forName == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(classClass);
        return nullptr;
    }
    jstring classNameRef = env->NewStringUTF(className.c_str());
    jobject classObj = env->CallStaticObjectMethod(classClass, forName, classNameRef);
    env->DeleteLocalRef(classNameRef);
    env->DeleteLocalRef(classClass);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (classObj != nullptr) {
            env->DeleteLocalRef(classObj);
        }
        return nullptr;
    }
    if (classObj == nullptr) {
        return nullptr;
    }
    jclass classGlobal = reinterpret_cast<jclass>(env->NewGlobalRef(classObj));
    env->DeleteLocalRef(classObj);
    if (classGlobal == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(g_classCacheMutex);
    g_classGlobalCache[className] = classGlobal;
    return classGlobal;
}

static bool isClassAssignable(JNIEnv* env, const std::string& baseClassName, const std::string& className) {
    if (baseClassName.empty() || className.empty()) {
        return false;
    }
    if (baseClassName == className) {
        return true;
    }
    const std::string key = baseClassName + "->" + className;
    {
        std::lock_guard<std::mutex> lock(g_classCacheMutex);
        auto it = g_assignableCache.find(key);
        if (it != g_assignableCache.end()) {
            return it->second;
        }
    }
    jclass baseClass = findClassGlobal(env, baseClassName);
    jclass targetClass = findClassGlobal(env, className);
    if (baseClass == nullptr || targetClass == nullptr) {
        std::lock_guard<std::mutex> lock(g_classCacheMutex);
        g_assignableCache[key] = false;
        return false;
    }
    jclass classClass = env->FindClass("java/lang/Class");
    if (classClass == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return false;
    }
    jmethodID isAssignableFrom = env->GetMethodID(classClass, "isAssignableFrom", "(Ljava/lang/Class;)Z");
    env->DeleteLocalRef(classClass);
    if (isAssignableFrom == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return false;
    }
    const bool matched = env->CallBooleanMethod(baseClass, isAssignableFrom, targetClass) == JNI_TRUE;
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    std::lock_guard<std::mutex> lock(g_classCacheMutex);
    g_assignableCache[key] = matched;
    return matched;
}

static void dispatchEvent(JNIEnv* env, const MethodEvent& event) {
    if (env == nullptr) {
        return;
    }
    if (!ensureDebugStackMotionClass(env)) {
        return;
    }
    jstring className = env->NewStringUTF(event.className.c_str());
    jstring methodName = env->NewStringUTF(event.methodName.c_str());
    jstring methodDesc = env->NewStringUTF(event.methodDesc.c_str());
    if (event.kind == MethodEvent::Kind::FIELD_MODIFICATION) {
        jstring fieldName = env->NewStringUTF(event.fieldName.c_str());
        env->CallStaticVoidMethod(g_debugStackMotionClass, g_onVariableVisitMethod,
                                  className, methodName, methodDesc, fieldName, nullptr, JNI_TRUE);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGE("Call onVariableVisit failed");
        }
        env->DeleteLocalRef(fieldName);
    } else {
        const jboolean isEnter = event.kind == MethodEvent::Kind::METHOD_ENTRY ? JNI_TRUE : JNI_FALSE;
        env->CallStaticVoidMethod(g_debugStackMotionClass, g_onMethodVisitMethod,
                                  className, methodName, methodDesc, isEnter);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGE("Call onMethodVisit failed");
        }
    }
    env->DeleteLocalRef(className);
    env->DeleteLocalRef(methodName);
    env->DeleteLocalRef(methodDesc);
}

static bool isInterestedMethodEvent(JNIEnv* env, const MethodEvent& event) {
    std::lock_guard<std::mutex> lock(g_interestMutex);
    if (g_interestedMethods.empty()) {
        return false;
    }
    for (const MethodInterest& interest : g_interestedMethods) {
        if (interest.methodName != event.methodName || interest.methodDesc != event.methodDesc) {
            continue;
        }
        if (interest.baseClassName.empty()) {
            continue;
        }
        if (event.className == interest.baseClassName) {
            return true;
        }
        if (interest.includeSubclasses &&
            isClassAssignable(env, interest.baseClassName, event.className)) {
            return true;
        }
    }
    return false;
}

static bool shouldDispatchEvent(JNIEnv* env, const MethodEvent& event) {
    if (event.className.empty()) {
        return false;
    }
    if (event.kind == MethodEvent::Kind::METHOD_ENTRY && !isInterestedMethodEvent(env, event)) {
        return false;
    }
    if (event.kind == MethodEvent::Kind::FIELD_MODIFICATION && event.fieldName.empty()) {
        return false;
    }
    return true;
}

static bool resolveReceiverRuntimeClass(jvmtiEnv* jvmti,
                                        JNIEnv* env,
                                        jthread thread,
                                        jmethodID method,
                                        std::string& outClassName,
                                        int64_t& outClassUniqueId) {
    outClassName.clear();
    outClassUniqueId = 0;
    if (jvmti == nullptr || env == nullptr || thread == nullptr || method == nullptr) {
        return false;
    }
    if (!g_canAccessLocalVariables.load(std::memory_order_relaxed)) {
        return false;
    }

    jint modifiers = 0;
    if (jvmti->GetMethodModifiers(method, &modifiers) != JVMTI_ERROR_NONE) {
        return false;
    }
    // JVM method access flag: static
    if ((modifiers & 0x0008) != 0) {
        return false;
    }

    jobject receiver = nullptr;
    jvmtiError localInstanceErr = jvmti->GetLocalInstance(thread, 0, &receiver);
    if (localInstanceErr != JVMTI_ERROR_NONE || receiver == nullptr) {
        if (receiver != nullptr) {
            env->DeleteLocalRef(receiver);
            receiver = nullptr;
        }
        if (jvmti->GetLocalObject(thread, 0, 0, &receiver) != JVMTI_ERROR_NONE || receiver == nullptr) {
            return false;
        }
    }
    jclass receiverClass = env->GetObjectClass(receiver);
    env->DeleteLocalRef(receiver);
    if (receiverClass == nullptr) {
        return false;
    }

    char* runtimeClassSignature = nullptr;
    if (jvmti->GetClassSignature(receiverClass, &runtimeClassSignature, nullptr) != JVMTI_ERROR_NONE) {
        env->DeleteLocalRef(receiverClass);
        return false;
    }
    outClassName = NormalizeClassName(runtimeClassSignature);
    outClassUniqueId = getOrAssignClassUniqueId(receiverClass);

    if (runtimeClassSignature != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(runtimeClassSignature));
    }
    env->DeleteLocalRef(receiverClass);
    return !outClassName.empty();
}

static int64_t getOrAssignClassUniqueId(jclass clazz) {
    if (g_jvmti == nullptr || clazz == nullptr) {
        return 0;
    }
    jlong tag = 0;
    if (g_jvmti->GetTag(clazz, &tag) != JVMTI_ERROR_NONE) {
        return 0;
    }
    if (tag != 0) {
        return static_cast<int64_t>(tag);
    }
    const int64_t nextId = g_nextClassUniqueId.fetch_add(1);
    if (g_jvmti->SetTag(clazz, static_cast<jlong>(nextId)) != JVMTI_ERROR_NONE) {
        return nextId;
    }
    jlong verifyTag = 0;
    if (g_jvmti->GetTag(clazz, &verifyTag) == JVMTI_ERROR_NONE && verifyTag != 0) {
        return static_cast<int64_t>(verifyTag);
    }
    return nextId;
}

static void enqueueMethodEvent(MethodEvent::Kind kind,
                               const std::string& className,
                               int64_t classUniqueId,
                               const char* methodName,
                               const char* methodDesc) {
    if (g_releaseInProgress.load(std::memory_order_relaxed)) {
        return;
    }
    if (methodName == nullptr || methodDesc == nullptr) {
        return;
    }
    MethodEvent event;
    event.kind = kind;
    event.className = className;
    event.classUniqueId = classUniqueId;
    event.methodName = methodName;
    event.methodDesc = methodDesc;
    startConsumerThread();
    {
        std::lock_guard<std::mutex> lock(g_queueMutex);
        g_eventQueue.emplace_back(std::move(event));
    }
    g_queueCv.notify_one();
}

static void enqueueFieldEvent(jvmtiEnv* jvmti,
                              jclass fieldClass,
                              const char* classSignature,
                              const char* methodName,
                              const char* methodDesc,
                              const char* fieldName,
                              char signatureType) {
    if (g_releaseInProgress.load(std::memory_order_relaxed)) {
        return;
    }
    if (jvmti == nullptr || fieldClass == nullptr || methodName == nullptr || methodDesc == nullptr) {
        return;
    }
    MethodEvent event;
    event.kind = MethodEvent::Kind::FIELD_MODIFICATION;
    event.className = NormalizeClassName(classSignature);
    event.classUniqueId = getOrAssignClassUniqueId(fieldClass);
    event.methodName = methodName;
    event.methodDesc = methodDesc;
    event.fieldName = fieldName == nullptr ? "" : fieldName;
    event.fieldSignatureType = signatureType;
    startConsumerThread();
    {
        std::lock_guard<std::mutex> lock(g_queueMutex);
        // 字段变化优先投递，避免被高频方法进栈事件淹没。
        g_eventQueue.emplace_front(std::move(event));
    }
    g_queueCv.notify_one();
}

static void clearEventQueue() {
    std::lock_guard<std::mutex> lock(g_queueMutex);
    g_eventQueue.clear();
}

static void consumerLoop() {
    JNIEnv* env = nullptr;
    if (g_vm == nullptr ||
        g_vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
        LOGE("Failed to attach consumer thread to JVM");
        g_consumerShouldRun.store(false);
        return;
    }
    while (true) {
        MethodEvent event{};
        {
            std::unique_lock<std::mutex> lock(g_queueMutex);
            g_queueCv.wait(lock, [] {
                return !g_eventQueue.empty() || !g_consumerShouldRun.load();
            });
            if (!g_consumerShouldRun.load() && g_eventQueue.empty()) {
                break;
            }
            event = std::move(g_eventQueue.front());
            g_eventQueue.pop_front();
        }
        // 在工作线程做事件过滤与分发，不在 JVMTI 回调线程做业务处理。
        if (!shouldDispatchEvent(env, event)) {
            continue;
        }
        dispatchEvent(env, event);
        g_forwardedEventCount.fetch_add(1, std::memory_order_relaxed);
    }
    g_vm->DetachCurrentThread();
}

static void startConsumerThread() {
    if (g_releaseInProgress.load(std::memory_order_relaxed)) {
        return;
    }
    bool expected = false;
    if (!g_consumerShouldRun.compare_exchange_strong(expected, true)) {
        return;
    }
    g_consumerThread = std::thread(consumerLoop);
}

static void stopConsumerThread() {
    if (!g_consumerShouldRun.exchange(false)) {
        clearEventQueue();
        return;
    }
    g_queueCv.notify_all();
    if (g_consumerThread.joinable()) {
        g_consumerThread.join();
    }
    clearEventQueue();
}

static void releaseInterestedMethods(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(g_interestMutex);
    for (MethodInterest& interest : g_interestedMethods) {
        if (interest.baseClassGlobal != nullptr) {
            env->DeleteGlobalRef(interest.baseClassGlobal);
            interest.baseClassGlobal = nullptr;
        }
        if (interest.methodRefGlobal != nullptr) {
            env->DeleteGlobalRef(interest.methodRefGlobal);
            interest.methodRefGlobal = nullptr;
        }
    }
    g_interestedMethods.clear();
    std::lock_guard<std::mutex> classLock(g_classCacheMutex);
    g_assignableCache.clear();
}

static bool hasWatchedField(JNIEnv* env, jclass ownerClass, jfieldID fieldId) {
    for (const WatchedField& watched : g_watchedFields) {
        if (watched.fieldId == fieldId &&
            watched.ownerClassGlobal != nullptr &&
            env->IsSameObject(watched.ownerClassGlobal, ownerClass) == JNI_TRUE) {
            return true;
        }
    }
    return false;
}

static void releaseWatchedFields(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(g_watchFieldMutex);
    for (WatchedField& watched : g_watchedFields) {
        if (g_jvmti != nullptr && watched.ownerClassGlobal != nullptr && watched.fieldId != nullptr) {
            g_jvmti->ClearFieldModificationWatch(watched.ownerClassGlobal, watched.fieldId);
        }
        if (watched.ownerClassGlobal != nullptr) {
            env->DeleteGlobalRef(watched.ownerClassGlobal);
            watched.ownerClassGlobal = nullptr;
        }
        watched.fieldId = nullptr;
    }
    g_watchedFields.clear();
}

static void disableJvmtiEvents() {
    if (g_jvmti == nullptr) {
        return;
    }
    g_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
    g_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_EXIT, nullptr);
    g_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FIELD_MODIFICATION, nullptr);
}

static void setFieldModificationEventEnabled(bool enabled) {
    if (g_jvmti == nullptr) {
        return;
    }
    const jvmtiEventMode mode = enabled ? JVMTI_ENABLE : JVMTI_DISABLE;
    jvmtiError err = g_jvmti->SetEventNotificationMode(mode, JVMTI_EVENT_FIELD_MODIFICATION, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        LOGE("Set FIELD_MODIFICATION mode failed: %d", err);
    } else {
        LOGI("FIELD_MODIFICATION mode -> %s", enabled ? "enabled" : "disabled");
    }
}

// JVMTI Event Callbacks
// 回调线程内仅采集原始参数并入队，不做业务处理。

void JNICALL onMethodEntry(jvmtiEnv* jvmti, JNIEnv* env, jthread thread, jmethodID method) {
    if (g_releaseInProgress.load(std::memory_order_relaxed)) {
        return;
    }
    if (jvmti == nullptr || env == nullptr || method == nullptr) {
        return;
    }
    char* methodName = nullptr;
    char* methodDesc = nullptr;
    char* classSignature = nullptr;
    jclass declaringClass = nullptr;

    if (jvmti->GetMethodName(method, &methodName, &methodDesc, nullptr) != JVMTI_ERROR_NONE) {
        return;
    }
    if (jvmti->GetMethodDeclaringClass(method, &declaringClass) != JVMTI_ERROR_NONE) {
        if (methodName != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
        }
        if (methodDesc != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodDesc));
        }
        return;
    }
    if (jvmti->GetClassSignature(declaringClass, &classSignature, nullptr) != JVMTI_ERROR_NONE) {
        env->DeleteLocalRef(declaringClass);
        if (methodName != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
        }
        if (methodDesc != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodDesc));
        }
        return;
    }

    std::string eventClassName = NormalizeClassName(classSignature);
    int64_t eventClassUniqueId = getOrAssignClassUniqueId(declaringClass);
    std::string runtimeClassName;
    int64_t runtimeClassUniqueId = 0;
    if (resolveReceiverRuntimeClass(jvmti, env, thread, method, runtimeClassName, runtimeClassUniqueId)) {
        eventClassName = runtimeClassName;
        if (runtimeClassUniqueId != 0) {
            eventClassUniqueId = runtimeClassUniqueId;
        }
    }

    enqueueMethodEvent(MethodEvent::Kind::METHOD_ENTRY, eventClassName, eventClassUniqueId,
                       methodName == nullptr ? "" : methodName, methodDesc == nullptr ? "" : methodDesc);

    env->DeleteLocalRef(declaringClass);
    if (methodName != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
    }
    if (methodDesc != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodDesc));
    }
    if (classSignature != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(classSignature));
    }
}

void JNICALL onFieldModification(jvmtiEnv* jvmti, JNIEnv* env, jthread /*thread*/, jmethodID method,
                                 jlocation /*location*/, jclass field_klass, jobject /*object*/, jfieldID field,
                                 char signature_type, jvalue /*new_value*/) {
    if (g_releaseInProgress.load(std::memory_order_relaxed)) {
        return;
    }
    if (jvmti == nullptr || env == nullptr || method == nullptr || field_klass == nullptr || field == nullptr) {
        return;
    }
    char* methodName = nullptr;
    char* methodDesc = nullptr;
    char* classSignature = nullptr;
    char* fieldName = nullptr;

    if (jvmti->GetMethodName(method, &methodName, &methodDesc, nullptr) != JVMTI_ERROR_NONE) {
        return;
    }
    if (jvmti->GetClassSignature(field_klass, &classSignature, nullptr) != JVMTI_ERROR_NONE) {
        if (methodName != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
        }
        if (methodDesc != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodDesc));
        }
        return;
    }
    if (jvmti->GetFieldName(field_klass, field, &fieldName, nullptr, nullptr) != JVMTI_ERROR_NONE) {
        if (methodName != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
        }
        if (methodDesc != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodDesc));
        }
        if (classSignature != nullptr) {
            jvmti->Deallocate(reinterpret_cast<unsigned char*>(classSignature));
        }
        return;
    }

    enqueueFieldEvent(jvmti, field_klass, classSignature,
                      methodName == nullptr ? "" : methodName,
                      methodDesc == nullptr ? "" : methodDesc,
                      fieldName, signature_type);

    if (methodName != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
    }
    if (methodDesc != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodDesc));
    }
    if (classSignature != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(classSignature));
    }
    if (fieldName != nullptr) {
        jvmti->Deallocate(reinterpret_cast<unsigned char*>(fieldName));
    }
}

// JVMTI Agent Entry
JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* /*options*/, void* /*reserved*/) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        LOGE("Failed to get JNIEnv");
        return JNI_ERR;
    }
    if (vm->GetEnv(reinterpret_cast<void**>(&g_jvmti), JVMTI_VERSION_1_2) != JNI_OK || g_jvmti == nullptr) {
        LOGE("Failed to get jvmtiEnv");
        return JNI_ERR;
    }

    jvmtiCapabilities caps = {0};
    // 仅开启方法进栈与字段修改监听。
    caps.can_generate_method_entry_events = 1;
    caps.can_generate_method_exit_events = 0;
    caps.can_generate_field_modification_events = 1;
    caps.can_tag_objects = 1;
    caps.can_access_local_variables = 1;
    jvmtiError err = g_jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        // 部分 ROM/设备可能不支持本能力，降级后继续工作。
        caps.can_access_local_variables = 0;
        err = g_jvmti->AddCapabilities(&caps);
        if (err != JVMTI_ERROR_NONE) {
            LOGE("AddCapabilities failed: %d", err);
            return JNI_ERR;
        }
    }
    jvmtiCapabilities enabledCaps = {0};
    if (g_jvmti->GetCapabilities(&enabledCaps) == JVMTI_ERROR_NONE) {
        g_canAccessLocalVariables.store(enabledCaps.can_access_local_variables == 1, std::memory_order_relaxed);
    }

    jvmtiEventCallbacks callbacks = {0};
    callbacks.MethodEntry = &onMethodEntry;
    callbacks.MethodExit = nullptr;
    callbacks.FieldModification = &onFieldModification;
    err = g_jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
        LOGE("SetEventCallbacks failed: %d", err);
        return JNI_ERR;
    }

    err = g_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        LOGE("Enable METHOD_ENTRY failed: %d", err);
        return JNI_ERR;
    }

    setFieldModificationEventEnabled(g_fieldModificationEnabled.load(std::memory_order_relaxed));

    startConsumerThread();
    LOGI("JVMTI Agent attached");
    return JNI_OK;
}

// JNI 方法实现
extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_registerMethodNative(JNIEnv* env, jclass /*clazz*/,
                                                                jclass baseClass, jobject methodObj,
                                                                jboolean includeSubclasses) {
    if (baseClass == nullptr || methodObj == nullptr || g_jvmti == nullptr) {
        return;
    }

    jmethodID methodId = env->FromReflectedMethod(methodObj);
    if (methodId == nullptr) {
        return;
    }

    char* name = nullptr;
    char* desc = nullptr;
    char* baseSignature = nullptr;
    if (g_jvmti->GetMethodName(methodId, &name, &desc, nullptr) != JVMTI_ERROR_NONE) {
        return;
    }
    if (g_jvmti->GetClassSignature(baseClass, &baseSignature, nullptr) != JVMTI_ERROR_NONE) {
        if (name != nullptr) {
            g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(name));
        }
        if (desc != nullptr) {
            g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(desc));
        }
        return;
    }

    const std::string methodNameStr = name == nullptr ? "" : name;
    const std::string methodDescStr = desc == nullptr ? "" : desc;
    const std::string baseClassNameStr = NormalizeClassName(baseSignature);
    const bool includeChildren = includeSubclasses == JNI_TRUE;

    {
        std::lock_guard<std::mutex> lock(g_interestMutex);
        for (const MethodInterest& interest : g_interestedMethods) {
            if (interest.methodName == methodNameStr &&
                interest.methodDesc == methodDescStr &&
                interest.baseClassName == baseClassNameStr &&
                interest.includeSubclasses == includeChildren) {
                if (name != nullptr) {
                    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(name));
                }
                if (desc != nullptr) {
                    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(desc));
                }
                if (baseSignature != nullptr) {
                    g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(baseSignature));
                }
                return;
            }
        }
        MethodInterest interest;
        interest.baseClassGlobal = reinterpret_cast<jclass>(env->NewGlobalRef(baseClass));
        interest.methodRefGlobal = env->NewGlobalRef(methodObj);
        interest.baseClassName = baseClassNameStr;
        interest.methodName = methodNameStr;
        interest.methodDesc = methodDescStr;
        interest.includeSubclasses = includeChildren;
        g_interestedMethods.emplace_back(std::move(interest));
    }

    if (name != nullptr) {
        g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(name));
    }
    if (desc != nullptr) {
        g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(desc));
    }
    if (baseSignature != nullptr) {
        g_jvmti->Deallocate(reinterpret_cast<unsigned char*>(baseSignature));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_clearRegisteredMethodsNative(JNIEnv* env, jclass /*clazz*/) {
    releaseInterestedMethods(env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_registerFieldNative(
        JNIEnv* env, jclass /*clazz*/, jclass ownerClass, jobject fieldObj) {
    if (env == nullptr || ownerClass == nullptr || fieldObj == nullptr || g_jvmti == nullptr) {
        return;
    }
    jfieldID fieldId = env->FromReflectedField(fieldObj);
    if (fieldId == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(g_watchFieldMutex);
        if (hasWatchedField(env, ownerClass, fieldId)) {
            return;
        }
    }
    jvmtiError err = g_jvmti->SetFieldModificationWatch(ownerClass, fieldId);
    if (err != JVMTI_ERROR_NONE) {
        LOGE("SetFieldModificationWatch failed: %d", err);
        return;
    }
    WatchedField watched;
    watched.ownerClassGlobal = reinterpret_cast<jclass>(env->NewGlobalRef(ownerClass));
    watched.fieldId = fieldId;
    if (watched.ownerClassGlobal == nullptr) {
        g_jvmti->ClearFieldModificationWatch(ownerClass, fieldId);
        LOGE("Failed to create global ref for watched field owner");
        return;
    }
    {
        std::lock_guard<std::mutex> lock(g_watchFieldMutex);
        g_watchedFields.emplace_back(std::move(watched));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_clearRegisteredFieldsNative(JNIEnv* env, jclass /*clazz*/) {
    releaseWatchedFields(env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_prepareCallbackBridgeNative(
        JNIEnv* env, jclass /*clazz*/, jclass bridgeClass) {
    if (!cacheDebugStackMotionClass(env, bridgeClass)) {
        LOGE("prepareCallbackBridgeNative failed");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_setFieldModificationEnabledNative(
        JNIEnv* /*env*/, jclass /*clazz*/, jboolean enabled) {
    const bool targetEnabled = enabled == JNI_TRUE;
    g_fieldModificationEnabled.store(targetEnabled, std::memory_order_relaxed);
    setFieldModificationEventEnabled(targetEnabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotionAgent_startAgentNative(JNIEnv* /*env*/, jclass /*clazz*/) {
    g_releaseInProgress.store(false, std::memory_order_relaxed);
    if (g_jvmti == nullptr) {
        LOGE("startAgentNative ignored: JVMTI env is null");
        return;
    }
    jvmtiError err = g_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        LOGE("startAgentNative enable METHOD_ENTRY failed: %d", err);
    }
    setFieldModificationEventEnabled(g_fieldModificationEnabled.load(std::memory_order_relaxed));
    startConsumerThread();
    LOGI("startAgentNative called");
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotionAgent_stopAgentNative(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (g_jvmti != nullptr) {
        g_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
        g_jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FIELD_MODIFICATION, nullptr);
    }
    stopConsumerThread();
    LOGI("stopAgentNative called");
}

extern "C" JNIEXPORT void JNICALL
Java_com_newchar_monitor_jvmti_DebugStackMotion_releaseNative(JNIEnv* env, jclass /*clazz*/) {
    g_releaseInProgress.store(true, std::memory_order_relaxed);
    disableJvmtiEvents();
    stopConsumerThread();
    releaseInterestedMethods(env);
    releaseWatchedFields(env);
    clearClassGlobalCache(env);
    releaseDebugStackMotionClass(env);
    LOGI("releaseNative called");
    g_releaseInProgress.store(false, std::memory_order_relaxed);
}
