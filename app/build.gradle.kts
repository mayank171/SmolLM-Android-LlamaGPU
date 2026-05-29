plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.shubham0204.startwithsmollm"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.shubham0204.startwithsmollm"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Native build for LlamaGPU with Vulkan support (using pre-built libs)
        ndk {
            abiFilters += listOf("arm64-v8a")  // Only arm64 for now
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }
    
    // Enable native build (comment out to disable GPU support and use original SmolLM only)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "gguf"
        noCompress += "onnx"  // For embedding model
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.material3.icons.extended)
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")

    // PDF parsing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    
    // Note: Tabula-Java is incompatible with PDFBox-Android
    // Using custom text-based table detection instead
    
    // ONNX Runtime for neural embeddings
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
    
    // ML Kit for OCR (text recognition from images)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // TODO: Step 1
    implementation(files("libs/smollm-debug.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}