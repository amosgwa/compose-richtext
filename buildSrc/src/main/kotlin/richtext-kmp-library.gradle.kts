import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
  id("maven-publish")
  id("signing")
}

repositories {
  google()
  mavenCentral()
}

kotlin {
  jvm()
  explicitApi()

  android {
    compileSdk = 36
    minSdk = AndroidConfiguration.minSdk

    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }
}

