import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Khoá ký cố định: mọi bản APK ký cùng khoá này nên cài đè (cập nhật) được, không mất dữ liệu.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Số bản build tăng dần mỗi lần GitHub build, để Android chấp nhận cập nhật.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.lqpicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lqpicker"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Khoá ký KHÔNG nằm trong repo (repo công khai). GitHub Actions khôi phục nó từ Secrets lúc build.
    // Ai tự build (fork) mà không có khoá thì dùng khoá debug, vẫn ra APK cài được.
    val hasKeystore = ksProps.getProperty("storePassword") != null &&
        rootProject.file(ksProps.getProperty("storeFile", "lqpicker.jks")).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(ksProps.getProperty("storeFile", "lqpicker.jks"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
            // Tắt thu gọn mã để bản đầu build chắc chắn; bật lại sau khi đã chạy ổn.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    // OCR chạy hoàn toàn trên máy, model đóng gói sẵn (không cần mạng).
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
