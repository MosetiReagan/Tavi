# Tavi Assistant - Production Proguard / R8 Shrinking Rules
# Preserves JNI boundaries, on-device AI runtime bindings, and serialized schemas.

# 1. Native JNI Bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Google AI Edge LiteRT-LM & TensorFlow Lite JNI
-keep class com.google.ai.edge.litert.** { *; }
-keep interface com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# 3. Google MediaPipe Tasks GenAI
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep interface com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.mediapipe.**

# 4. Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# 5. Tavi Data Entities & Tool Signatures
-keep class com.example.tavi.model.** { *; }
-keep class com.example.tavi.workflow.** { *; }
-keep class com.example.tavi.data.** { *; }
-keep class com.example.tavi.tools.AssistantTool { *; }
-keep class com.example.tavi.tools.ToolParameter { *; }
-keep class com.example.tavi.tools.ToolResult { *; }

# 6. Android Support & Coroutines
-dontwarn java.lang.invoke.MethodHandles$Lookup
-dontwarn java.lang.invoke.MethodType
-dontwarn javax.annotation.**
-keepattributes EnclosingMethod, Signature
