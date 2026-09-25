plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties
import groovy.json.JsonSlurper

val publishingFile = rootProject.file("publishing.json")
val publishingConfig: Map<*, *> = if (publishingFile.exists()) JsonSlurper().parse(publishingFile) as Map<*, *> else emptyMap<String, Any>()
val managed = publishingFile.exists()
val iconFile = rootProject.file("publishing-icon.png")
val generatedPublishing = layout.buildDirectory.dir("generated/publishing")
val generatePublishing by tasks.registering {
    inputs.files(publishingFile, iconFile).optional()
    outputs.dir(generatedPublishing)
    doLast {
        val output = generatedPublishing.get().asFile
        output.deleteRecursively()
        output.resolve("assets").mkdirs()
        output.resolve("res").mkdirs()
        if (managed) publishingFile.copyTo(output.resolve("assets/publishing.json"), overwrite = true)
        if (managed && iconFile.exists()) {
            output.resolve("res/drawable-nodpi").mkdirs()
            iconFile.copyTo(output.resolve("res/drawable-nodpi/publishing_icon.png"), overwrite = true)
        }
    }
}
tasks.named("preBuild").configure { dependsOn(generatePublishing) }

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigning = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    }
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = (publishingConfig["versionCode"] as? Number)?.toInt() ?: 1
        versionName = publishingConfig["versionName"] as? String ?: "1.0"
        buildConfigField("boolean", "PUBLISHING_MANAGED", managed.toString())
        resValue("string", "publishing_app_name", (publishingConfig["appName"] as? String ?: "游戏中心").replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'").replace("\"", "\\\""))
        manifestPlaceholders["publishingEnabled"] = managed.toString()
        manifestPlaceholders["auroraEnabled"] = (!managed).toString()
        manifestPlaceholders["publishingIcon"] = if (managed && iconFile.exists()) "@drawable/publishing_icon" else "@mipmap/ic_launcher"
        manifestPlaceholders["applicationLabel"] = if (managed) "@string/publishing_app_name" else "@string/app_name"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main") {
        assets.srcDir(generatedPublishing.map { it.dir("assets") })
        res.srcDir(generatedPublishing.map { it.dir("res") })
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "CONTAINER_VERSION", "\"2.0.0\"")
            buildConfigField("String", "BUILT_IN_H5_VERSION", "\"1.0.0\"")
            buildConfigField("String", "REMOTE_MANIFEST_URL", "\"\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "CONTAINER_VERSION", "\"2.0.0\"")
            buildConfigField("String", "BUILT_IN_H5_VERSION", "\"1.0.0\"")
            buildConfigField("String", "REMOTE_MANIFEST_URL", "\"\"")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
