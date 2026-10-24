// ===== 应用层 =====
// 约定：不得在此文件写入流水线 / 签名 / 环境相关逻辑，
//      保证 app/ 模块可被整体替换而流水线无需改动。

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fred.weatherclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fred.weatherclock"
        minSdk = 21          // Android 5.0：本项目面向旧手机，门槛必须放低
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // 优化点 #7：限制语言资源，减少资源合并开销
        resourceConfigurations.add("zh")
    }

    buildTypes {
        getByName("debug") {
            // 优化点 #5：debug 关闭 R8 与资源缩减（构建中最耗时的一步）
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    // 优化点 #6：依赖最小化。
    // 本项目刻意不引入 AppCompat / Material：主题用系统内置 Theme.Material（API 21+），
    // 网络用 HttpURLConnection，JSON 用内置 org.json，图标用 vector drawable。
    // 好处：编译快、体积小、老手机跑得动。
    implementation("androidx.core:core-ktx:1.15.0")
}
