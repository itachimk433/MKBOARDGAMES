plugins {
    id("com.android.application") version "8.9.2" apply false
    kotlin("android") version "1.9.0" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
