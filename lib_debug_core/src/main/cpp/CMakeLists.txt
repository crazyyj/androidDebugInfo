# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("jvmti")
set(JAVA_HOME $ENV{JAVA_HOME})

include_directories(
        ${JAVA_HOME}/include
        ${JAVA_HOME}/include/darwin)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        jvmti.cpp)

# Android Gradle Plugin 的 release native 构建通常使用 RelWithDebInfo，
# 因此 Release/RelWithDebInfo/MinSizeRel 都开启符号隐藏、段裁剪与 strip。
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        $<$<CONFIG:Release>:-Oz>
        $<$<CONFIG:RelWithDebInfo>:-Oz>
        $<$<CONFIG:MinSizeRel>:-Oz>
        $<$<CONFIG:Release>:-DNDEBUG>
        $<$<CONFIG:RelWithDebInfo>:-DNDEBUG>
        $<$<CONFIG:MinSizeRel>:-DNDEBUG>
        $<$<CONFIG:Release>:-fvisibility=hidden>
        $<$<CONFIG:RelWithDebInfo>:-fvisibility=hidden>
        $<$<CONFIG:MinSizeRel>:-fvisibility=hidden>
        $<$<CONFIG:Release>:-fvisibility-inlines-hidden>
        $<$<CONFIG:RelWithDebInfo>:-fvisibility-inlines-hidden>
        $<$<CONFIG:MinSizeRel>:-fvisibility-inlines-hidden>
        $<$<CONFIG:Release>:-ffunction-sections>
        $<$<CONFIG:RelWithDebInfo>:-ffunction-sections>
        $<$<CONFIG:MinSizeRel>:-ffunction-sections>
        $<$<CONFIG:Release>:-fdata-sections>
        $<$<CONFIG:RelWithDebInfo>:-fdata-sections>
        $<$<CONFIG:MinSizeRel>:-fdata-sections>)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        $<$<CONFIG:Release>:-Wl,--gc-sections>
        $<$<CONFIG:RelWithDebInfo>:-Wl,--gc-sections>
        $<$<CONFIG:MinSizeRel>:-Wl,--gc-sections>
        $<$<CONFIG:Release>:-Wl,--icf=safe>
        $<$<CONFIG:RelWithDebInfo>:-Wl,--icf=safe>
        $<$<CONFIG:MinSizeRel>:-Wl,--icf=safe>
        $<$<CONFIG:Release>:-Wl,--exclude-libs,ALL>
        $<$<CONFIG:RelWithDebInfo>:-Wl,--exclude-libs,ALL>
        $<$<CONFIG:MinSizeRel>:-Wl,--exclude-libs,ALL>
        $<$<CONFIG:Release>:-Wl,--strip-all>
        $<$<CONFIG:RelWithDebInfo>:-Wl,--strip-all>
        $<$<CONFIG:MinSizeRel>:-Wl,--strip-all>)


# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log)