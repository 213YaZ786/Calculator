import java.net.URI
import java.security.MessageDigest

// Giac, the computer algebra system: see fetchGiac below.
val giacCommit = "bdbb1c41fe52d8b4fb7c1d15c2deaea88fc3803c"
val giacSha = "0b5ddcad99da975cc8b6c21c01e8c42ddd4b058fb38fba72ee438727aa577caa"
val giacRoot = layout.buildDirectory.dir("giac").get().asFile
val giacSrc = File(giacRoot, "giac-$giacCommit")

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.yaz.calculator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yaz.calculator"
        minSdk = 31
        targetSdk = 37
        versionCode = 10
        versionName = "0.3.2"
        // Giac, for 64-bit ARM phones (the emulators run it translated): every
        // native library of the APK for that one, so it is the one Android picks.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a")
                arguments("-DGIAC_SRC=${giacSrc.absolutePath}", "-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("calculator.storeFile").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("calculator.storePassword").orNull
                keyAlias = providers.gradleProperty("calculator.keyAlias").orNull
                keyPassword = providers.gradleProperty("calculator.keyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Only attached when the signing properties are supplied, so a
            // local build without a keystore still produces an unsigned APK.
            if (providers.gradleProperty("calculator.storeFile").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES"
        )
        // Giac compressed in the APK (8 MB instead of 19), unpacked once at install.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Numerical analysis for the advanced mode (roots, extrema, integrals), Apache 2.0, pure Java.
    implementation("org.hipparchus:hipparchus-core:4.0.3")
    implementation("org.hipparchus:hipparchus-optim:4.0.3")

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// The computer algebra system: Giac (GPL-3.0, Bernard Parisse), from
// GeoGebra's mirror (github.com/geogebra/giac) at the pinned commit, its
// archive checked against its SHA-256, then built here. One fix applied:
// a 24-byte buffer read with 32 bytes in global.cc (Android's FORTIFY
// refuses it).
val fetchGiac by tasks.registering {
    val root = giacRoot
    val src = giacSrc
    val commit = giacCommit
    val sha = giacSha
    inputs.property("commit", commit)
    outputs.dir(src)
    doLast {
        if (File(src, "src/giac/cpp/global.cc").exists()) return@doLast
        val bytes = URI("https://github.com/geogebra/giac/archive/$commit.tar.gz").toURL().openStream().use { it.readBytes() }
        val got = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(got == sha) { "Giac: checksum does not match" }
        root.mkdirs()
        val archive = File(root, "giac.tar.gz").apply { writeBytes(bytes) }
        val tar = ProcessBuilder("tar", "xzf", archive.path, "-C", root.path, "giac-$commit/src/giac", "giac-$commit/src/jni/prebuilt/android").inheritIO().start()
        check(tar.waitFor() == 0) { "Giac: could not unpack" }
        archive.delete()
        val global = File(src, "src/giac/cpp/global.cc")
        val fixed = global.readText().replace("unsigned char buf[24];\n    int i=fread(buf,1,32,f);", "unsigned char buf[32];\n    int i=fread(buf,1,32,f);")
        check(fixed != global.readText()) { "Giac: the buffer fix no longer applies" }
        global.writeText(fixed)
    }
}
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name.startsWith("generateJsonModel")) dependsOn(fetchGiac)
}

