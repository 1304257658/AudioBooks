plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.audiobooks"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.audiobooks"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.2.5"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    androidResources {
        noCompress += listOf("onnx", "bin", "far", "fst", "txt")
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                "AudioBooks-${variant.name}-v${output.versionName.get()}-${output.versionCode.get()}.apk"
            )
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.jsoup)
    implementation(libs.pdfbox.android)

    debugImplementation(libs.compose.ui.tooling)
}
