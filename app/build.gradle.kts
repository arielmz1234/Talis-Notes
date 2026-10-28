import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.tali.notes"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.tali.notes"
    // Android 10: the oldest Galaxy Tab S6 Lite firmware, and the floor for low-latency inking.
    minSdk = 29
    targetSdk = 36
    versionCode = 18
    versionName = "1.0.8"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Google Drive backup needs a one-time OAuth client in Google Cloud (see BACKUP_SETUP.md).
    // Set this to true once that exists; until then the Drive option stays hidden.
    buildConfigField("boolean", "DRIVE_BACKUP", "true")
    // Where crash reports go (the library's Send report).
    buildConfigField("String", "SUPPORT_EMAIL", "\"arielmz12344@gmail.com\"")
  }

  // The release key lives in keystore/ (not in git). Every update must be signed with the same key,
  // or Android refuses to install it over the existing app, so keep a copy of that folder safe.
  val releaseKey = rootProject.file("keystore/keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
  signingConfigs {
    if (releaseKey != null) {
      create("release") {
        storeFile = rootProject.file(releaseKey.getProperty("storeFile"))
        storePassword = releaseKey.getProperty("storePassword")
        keyAlias = releaseKey.getProperty("keyAlias")
        keyPassword = releaseKey.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
    // For the owner's own tablet: as fast as a release (R8, not debuggable), but signed with the
    // debug key, so it installs over his debug build and keeps his notes. Debug builds run Compose
    // several times slower, which made animations look choppy that are smooth for Tali.
    create("preview") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }
  lint {
    // Pencil and calligraphy brushes use Ink's custom-brush API, which is marked library-internal in 1.0.
    disable += "RestrictedApi"
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)
  testImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Ink: low-latency stylus rendering, brushes, geometry and stroke serialization.
  implementation(libs.androidx.ink.authoring)
  implementation(libs.androidx.ink.brush)
  implementation(libs.androidx.ink.geometry)
  implementation(libs.androidx.ink.rendering)
  implementation(libs.androidx.ink.strokes)
  implementation(libs.androidx.ink.storage)
  implementation(libs.androidx.input.motionprediction)

  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.work.runtime)
  implementation(libs.play.services.auth)
  // Our own camera and scanner screen (ui/camera).
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // Compose UI tests on the JVM (Robolectric), e.g. the gliding selection highlight.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.test.core)

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
