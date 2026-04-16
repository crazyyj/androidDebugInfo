# JVMTI bridge: native 通过固定类名与方法名查找/回调，不能被 R8 重命名。
-keep class com.newchar.debug.monitor.jvmti.DebugStackMotion { *; }
-keep class com.newchar.debug.monitor.jvmti.DebugStackMotionAgent { *; }
-keep class com.newchar.debug.monitor.jvmti.DebugStackMotionCallback { *; }
-keep class com.newchar.debug.monitor.jvmti.DebugStackMotion$RawMethodCallback { *; }
-keep class com.newchar.debug.monitor.jvmti.DebugStackMotion$RawVariableCallback { *; }
