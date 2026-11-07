import java.security.MessageDigest

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.tavi"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.tavi.assistant"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        providers.gradleProperty("taviTargetAbi").orNull?.let { targetAbi ->
            require(targetAbi in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) { "Unsupported taviTargetAbi: $targetAbi" }
            ndk.abiFilters.add(targetAbi)
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE") ?: project.findProperty("KEYSTORE_FILE")?.toString()
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
            val keyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS")?.toString()
            val keyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD")?.toString()

            if (!keystoreFile.isNullOrEmpty() && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        checkDependencies = false
        ignoreTestSources = true
        disable += setOf("TypographyFractions", "TypographyQuotes", "GradleDependency")
    }

    testOptions {
      unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

val speechRuntimeVerification by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val localSpeechAar = providers.gradleProperty("taviSpeechAar").orNull
val speechRuntimeDependency = "com.k2fsa.sherpa.onnx:sherpa-onnx-static-link-onnxruntime:1.12.20@aar"
val verifySpeechRuntime = tasks.register("verifySpeechRuntime") {
    val artifacts = if (localSpeechAar == null) speechRuntimeVerification else files(localSpeechAar)
    inputs.files(artifacts)
    doLast {
        val artifact = artifacts.singleFile
        val digest = MessageDigest.getInstance("SHA-256")
        artifact.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == "c8a88a8390ea699b24c49a31c56e8b2e3515c5cd5a2a49f8555a3b3fd68d253a") {
            "Offline speech runtime failed its publisher SHA-256 verification."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifySpeechRuntime) }

dependencies {
  if (localSpeechAar == null) {
      implementation(speechRuntimeDependency)
      add(speechRuntimeVerification.name, speechRuntimeDependency)
  } else {
      implementation(files(localSpeechAar))
  }
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.org.json)

  // MediaPipe Tasks GenAI for on-device Gemma inference (legacy fallback)
  implementation(libs.mediapipe.tasks.genai)

  // Google AI Edge LiteRT-LM for next-gen on-device LLM inference
  implementation(libs.litertlm.android)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
