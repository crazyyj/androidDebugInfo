package com.newchar.debug.monitor.jvmti;

import android.os.Build;
import android.os.Debug;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class DebugStackMotion {

    private static int init;
    private static volatile boolean fieldModificationEnabled;
    private static volatile boolean callbackBridgePrepared;
    private static DebugStackMotionCallback callback;
    private static final Set<RawMethodCallback> rawMethodCallbacks = new CopyOnWriteArraySet<>();
    private static final Set<RawVariableCallback> rawVariableCallbacks = new CopyOnWriteArraySet<>();
    private static final Set<Method> monitoredMethods = new HashSet<>();
    private static final Map<String, Field> monitoredFields = new HashMap<>();

    static {
        attachAgentSys("jvmti");
    }

    /**
     * 加载并 attach jvmti agent。
     *
     * @param agentLib agent 库名
     */
    private static void attachAgentSys(String agentLib) {
        try {
            Log.i("AAA", "准备加载 agent");
            if (init > 0) {
                return;
            }
            init = 1;
            Log.i("AAA", "正在加载 agent");
            System.loadLibrary(agentLib);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Debug.attachJvmtiAgent("lib" + agentLib + ".so", null, Thread.currentThread().getContextClassLoader());
                Log.i("AAA", "加载完成 agent1");
            } else {
                Class<?> vmDebug = Class.forName("dalvik.system.VMDebug");
                Method method = vmDebug.getDeclaredMethod("attachAgent", ClassLoader.class);
                method.setAccessible(true);
                method.invoke(vmDebug, "lib" + agentLib + ".so", Thread.currentThread().getContextClassLoader());
                method.setAccessible(false);
                Log.i("AAA", "加载完成 agent2");
            }
            init = 2;
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("AAA", "加载结果 agent3 错误了 \n", e);
            init = -1;
        } finally {
            callbackAttachResult(init);
        }
    }

    /**
     * 设置回调处理器。
     *
     * @param cb 回调
     */
    public static void setCallback(DebugStackMotionCallback cb) {
        ensureCallbackBridgePrepared();
        callback = cb;
    }

    /**
     * 设置轻量方法事件回调，直接透传 native 上报的原始参数。
     *
     * @param cb 回调
     */
    public static void setRawMethodCallback(RawMethodCallback cb) {
        ensureCallbackBridgePrepared();
        rawMethodCallbacks.clear();
        if (cb != null) {
            rawMethodCallbacks.add(cb);
        }
    }

    /**
     * 追加轻量方法事件回调。
     */
    public static void addRawMethodCallback(RawMethodCallback cb) {
        if (cb == null) {
            return;
        }
        ensureCallbackBridgePrepared();
        rawMethodCallbacks.add(cb);
    }

    /**
     * 移除轻量方法事件回调。
     */
    public static void removeRawMethodCallback(RawMethodCallback cb) {
        if (cb == null) {
            return;
        }
        rawMethodCallbacks.remove(cb);
    }

    /**
     * 设置轻量字段修改回调，直接透传 native 上报的原始参数。
     *
     * @param cb 回调
     */
    public static void setRawVariableCallback(RawVariableCallback cb) {
        ensureCallbackBridgePrepared();
        rawVariableCallbacks.clear();
        if (cb != null) {
            rawVariableCallbacks.add(cb);
        }
    }

    /**
     * 追加轻量字段修改回调。
     */
    public static void addRawVariableCallback(RawVariableCallback cb) {
        if (cb == null) {
            return;
        }
        ensureCallbackBridgePrepared();
        rawVariableCallbacks.add(cb);
    }

    /**
     * 移除轻量字段修改回调。
     */
    public static void removeRawVariableCallback(RawVariableCallback cb) {
        if (cb == null) {
            return;
        }
        rawVariableCallbacks.remove(cb);
    }

    /**
     * 动态开关字段修改事件（JVMTI_EVENT_FIELD_MODIFICATION）。
     *
     * @param enabled true: 启用；false: 关闭
     */
    public static void setFieldModificationEnabled(boolean enabled) {
        ensureCallbackBridgePrepared();
        fieldModificationEnabled = enabled;
        setFieldModificationEnabledNative(enabled);
    }

    /**
     * 当前字段修改事件开关状态（Java 侧缓存）。
     */
    public static boolean isFieldModificationEnabled() {
        return fieldModificationEnabled;
    }

    /**
     * 注册需要监听的方法。
     *
     * @param method 方法
     */
    public static void registerMethod(Method method) {
        registerMethod(method, false);
    }

    /**
     * 注册需要监听的方法。
     *
     * @param method             方法
     * @param includeSubclasses  是否包含子类
     */
    public static void registerMethod(Method method, boolean includeSubclasses) {
        if (method == null) {
            return;
        }
        registerMethod(method.getDeclaringClass(), method, includeSubclasses);
    }

    /**
     * 注册需要监听的方法。
     *
     * @param baseClass          归属类
     * @param method             方法
     * @param includeSubclasses  是否包含子类
     */
    public static void registerMethod(Class<?> baseClass, Method method, boolean includeSubclasses) {
        if (method == null || baseClass == null) {
            return;
        }
        monitoredMethods.add(method);
        registerMethodNative(baseClass, method, includeSubclasses);
    }

    /**
     * 清理已注册的方法监听。
     */
    public static void clearRegisteredMethods() {
        monitoredMethods.clear();
        clearRegisteredMethodsNative();
    }

    /**
     * 释放当前监听资源。
     */
    public static void release() {
        callback = null;
        rawMethodCallbacks.clear();
        rawVariableCallbacks.clear();
        callbackBridgePrepared = false;
        monitoredMethods.clear();
        monitoredFields.clear();
        clearRegisteredMethodsNative();
        clearRegisteredFieldsNative();
        releaseNative();
    }

    /**
     * 注册字段修改监听（字段必须先注册，JVMTI 才会上报 FieldModification）。
     */
    public static void registerField(Field field) {
        if (field == null) {
            return;
        }
        registerField(field.getDeclaringClass(), field);
    }

    /**
     * 按类+字段名注册字段修改监听。
     */
    public static void registerField(Class<?> ownerClass, String fieldName) {
        if (ownerClass == null || fieldName == null || fieldName.isEmpty()) {
            return;
        }
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            registerField(ownerClass, field);
        } catch (NoSuchFieldException ignored) {
        }
    }

    /**
     * 注册字段修改监听。
     */
    public static void registerField(Class<?> ownerClass, Field field) {
        if (ownerClass == null || field == null) {
            return;
        }
        ensureCallbackBridgePrepared();
        String fieldKey = ownerClass.getName() + "#" + field.getName();
        if (monitoredFields.containsKey(fieldKey)) {
            return;
        }
        monitoredFields.put(fieldKey, field);
        registerFieldNative(ownerClass, field);
    }

    /**
     * 清理已注册的字段监听。
     */
    public static void clearRegisteredFields() {
        monitoredFields.clear();
        clearRegisteredFieldsNative();
    }

    /**
     * 提供给 native 的 Method 监听接口。
     *
     * @param clazz     类
     * @param outMethod 输出方法数组
     */
    public static native void attachClass(Class<?> clazz, Method[] outMethod);

    /**
     * 预留初始化入口。
     */
    public static void init() {
        ensureCallbackBridgePrepared();
    }

    private static void ensureCallbackBridgePrepared() {
        if (callbackBridgePrepared) {
            return;
        }
        try {
            prepareCallbackBridgeNative(DebugStackMotion.class);
            callbackBridgePrepared = true;
        } catch (Throwable t) {
            Log.w("AAA", "prepare callback bridge failed", t);
        }
    }

    /**
     * JNI 回调方法：方法进出。
     *
     * @param className  类名签名
     * @param methodName 方法名
     * @param methodDesc 方法描述
     * @param isEnter    是否进入方法
     */
    public static void onMethodVisit(String className, String methodName, String methodDesc, boolean isEnter) {
        for (RawMethodCallback rawCb : rawMethodCallbacks) {
            rawCb.onMethodVisit(className, methodName, methodDesc, isEnter);
        }
        DebugStackMotionCallback cb = callback;
        if (cb == null) {
            return;
        }
        Method method = findMethodBySignature(className, methodName, methodDesc);
        if (method == null) {
            return;
        }
        cb.onMethodVisit(method, isEnter, new Throwable());
    }

    /**
     * JNI 回调方法：字段访问。
     *
     * @param className  类名签名
     * @param methodName 方法名
     * @param methodDesc 方法描述
     * @param fieldName  字段名
     * @param newValue   新值
     * @param isSet      是否为设置操作
     */
    public static void onVariableVisit(String className, String methodName, String methodDesc, String fieldName,
            Object newValue, boolean isSet) {
        for (RawVariableCallback rawCb : rawVariableCallbacks) {
            rawCb.onVariableVisit(className, methodName, methodDesc, fieldName, newValue, isSet);
        }
        DebugStackMotionCallback cb = callback;
        if (cb == null) {
            return;
        }
        Field field = findFieldByName(className, fieldName);
        if (field == null) {
            return;
        }
        cb.onVariableVisit(field, isSet, new Throwable());
    }

    /**
     * 根据签名查找方法。
     *
     * @param className  类名签名
     * @param methodName 方法名
     * @param methodDesc 方法描述符
     * @return 方法
     */
    private static Method findMethodBySignature(String className, String methodName, String methodDesc) {
        try {
            Class<?> clazz = Class.forName(normalizeClassName(className));
            return findMethod(clazz, methodName, methodDesc);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    /**
     * 根据类名与字段名查找字段。
     *
     * @param className 类名签名
     * @param fieldName 字段名
     * @return 字段
     */
    private static Field findFieldByName(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(normalizeClassName(className));
            return clazz.getDeclaredField(fieldName);
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            return null;
        }
    }

    /**
     * 处理类名签名为标准类名。
     *
     * @param signature 类名签名
     * @return 类名
     */
    private static String normalizeClassName(String signature) {
        if (signature == null || signature.isEmpty()) {
            return signature;
        }
        String className = signature;
        if (className.charAt(0) == 'L' && className.charAt(className.length() - 1) == ';') {
            className = className.substring(1, className.length() - 1);
        }
        return className.replace('/', '.');
    }

    /**
     * 根据方法名与描述符查找方法。
     *
     * @param clazz      类
     * @param methodName 方法名
     * @param methodDesc 方法描述符
     * @return 方法
     */
    private static Method findMethod(Class<?> clazz, String methodName, String methodDesc) {
        Class<?> current = clazz;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                if (!m.getName().equals(methodName) || methodDesc == null) {
                    continue;
                }
                String desc = getMethodDescriptor(m);
                if (desc.equals(methodDesc)) {
                    return m;
                }
            }
            Method interfaceMethod = findMethodInInterfaces(current.getInterfaces(), methodName, methodDesc);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMethodInInterfaces(Class<?>[] interfaces, String methodName, String methodDesc) {
        if (interfaces == null || interfaces.length == 0) {
            return null;
        }
        for (Class<?> interfaceClass : interfaces) {
            if (interfaceClass == null) {
                continue;
            }
            for (Method m : interfaceClass.getDeclaredMethods()) {
                if (!m.getName().equals(methodName) || methodDesc == null) {
                    continue;
                }
                String desc = getMethodDescriptor(m);
                if (desc.equals(methodDesc)) {
                    return m;
                }
            }
            Method nested = findMethodInInterfaces(interfaceClass.getInterfaces(), methodName, methodDesc);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * 获取方法的 JVM 描述符。
     *
     * @param method 方法
     * @return 描述符
     */
    private static String getMethodDescriptor(Method method) {
        StringBuilder desc = new StringBuilder();
        desc.append("(");
        for (Class<?> paramType : method.getParameterTypes()) {
            desc.append(getTypeDescriptor(paramType));
        }
        desc.append(")");
        desc.append(getTypeDescriptor(method.getReturnType()));
        return desc.toString();
    }

    /**
     * 获取类型的 JVM 描述符。
     *
     * @param clazz 类型
     * @return 描述符
     */
    private static String getTypeDescriptor(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            if (clazz == void.class) return "V";
            else if (clazz == int.class) return "I";
            else if (clazz == boolean.class) return "Z";
            else if (clazz == byte.class) return "B";
            else if (clazz == char.class) return "C";
            else if (clazz == short.class) return "S";
            else if (clazz == long.class) return "J";
            else if (clazz == float.class) return "F";
            else if (clazz == double.class) return "D";
        } else if (clazz.isArray()) {
            return clazz.getName().replace('.', '/');
        } else {
            return "L" + clazz.getName().replace('.', '/') + ";";
        }
        return "";
    }

    /**
     * 给 jni 提供是否 attach 成功的回调。
     *
     * @param result 结果 2 成功
     */
    private static void callbackAttachResult(int result) {
        Log.i("AAA", "加载结果 agent3 " + result);
    }

    /**
     * native 方法：注册监听方法。
     *
     * @param baseClass         归属类
     * @param method            方法
     * @param includeSubclasses 是否包含子类
     */
    private static native void registerMethodNative(Class<?> baseClass, Method method, boolean includeSubclasses);

    /**
     * native 方法：清理已注册的方法。
     */
    private static native void clearRegisteredMethodsNative();

    /**
     * native 方法：注册字段监听。
     */
    private static native void registerFieldNative(Class<?> ownerClass, Field field);

    /**
     * native 方法：清理字段监听。
     */
    private static native void clearRegisteredFieldsNative();

    /**
     * native 方法：释放资源。
     */
    private static native void releaseNative();

    /**
     * native 方法：动态开关字段修改事件。
     */
    private static native void setFieldModificationEnabledNative(boolean enabled);

    /**
     * native 方法：预缓存 DebugStackMotion 回调桥接类与方法。
     */
    private static native void prepareCallbackBridgeNative(Class<?> bridgeClass);

    /**
     * 轻量方法回调：不做反射，直接消费原始事件参数。
     */
    public interface RawMethodCallback {
        void onMethodVisit(String className, String methodName, String methodDesc, boolean isEnter);
    }

    /**
     * 轻量字段修改回调：不做反射，直接消费原始事件参数。
     */
    public interface RawVariableCallback {
        void onVariableVisit(String className, String methodName, String methodDesc,
                String fieldName, Object newValue, boolean isSet);
    }
}
