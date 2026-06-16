import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
  id("maven-publish")
  id("signing")
}

// Coordinates for published artifacts. The Maven Central (vanniktech) plugin used to
// derive these from GROUP/VERSION_NAME; after dropping it for JitPack, wire them onto
// the bare maven-publish plugin so publications get real coordinates instead of
// "compose-richtext:…:unspecified".
group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

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

