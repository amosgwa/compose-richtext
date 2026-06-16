import org.gradle.api.publish.maven.MavenPublication
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

// Re-declare the Apache-2.0 license on every publication's POM. The vanniktech
// plugin used to emit this; bare maven-publish does not, so consumers' Licensee
// checks fail with "Artifact declares no licenses!". Name/url mirror the prior
// (vanniktech) POMs so SPDX matching keeps resolving to Apache-2.0.
publishing {
  publications.withType(MavenPublication::class.java).configureEach {
    pom {
      licenses {
        license {
          name.set("The Apache Software License, Version 2.0")
          url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
          distribution.set("repo")
        }
      }
    }
  }
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

