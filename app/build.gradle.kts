plugins {
      id("com.android.application")
      kotlin("android")
  }

  android {
      compileSdk = 36
      namespace = "com.mkdev.mkboardgames"

      defaultConfig {
          applicationId = "com.mkdev.mkboardgames"
          minSdk = 24
          targetSdk = 36
          versionCode = 25
          versionName = "2.5"
      }

      signingConfigs {
          create("release") {
              storeFile     = file("release.keystore")
              storePassword = System.getenv("STORE_PASSWORD") ?: ""
              keyAlias      = System.getenv("KEY_ALIAS") ?: ""
              keyPassword   = System.getenv("KEY_PASSWORD") ?: ""
              storeType     = "PKCS12"
          }
      }

      buildTypes {
          release {
              isMinifyEnabled = false
              signingConfig   = signingConfigs.getByName("release")
          }
      }

      compileOptions {
          sourceCompatibility = JavaVersion.VERSION_11
          targetCompatibility = JavaVersion.VERSION_11
      }

      kotlinOptions {
          jvmTarget = "11"
      }
  }

  dependencies {
      implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
      implementation("androidx.core:core-ktx:1.12.0")
      implementation("androidx.appcompat:appcompat:1.6.1")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
      implementation("com.google.android.gms:play-services-ads:23.6.0")
      implementation("com.android.billingclient:billing-ktx:7.1.1")

      testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
  }
  