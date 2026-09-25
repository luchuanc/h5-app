plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties
import java.security.MessageDigest
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

val publishingFile = rootProject.file("publishing.json")
val publishingDefaults = rootProject.file("publishing.defaults.json")
val publishingConfig = mutableMapOf<String, Any?>().apply {
    for (file in listOf(publishingDefaults, publishingFile)) {
        if (file.exists()) (JsonSlurper().parse(file) as Map<*, *>).forEach { (key, value) -> put(key as String, value) }
    }
}
val managed = publishingConfig.isNotEmpty()
val iconFile = rootProject.file("publishing-icon.png")
val gameIconDir = rootProject.file("publishing-icons")
val manifestTemplate = rootProject.file("app/src/main/AndroidManifest.xml")
val generatedPublishing = layout.buildDirectory.dir("generated/publishing")
val generatePublishing by tasks.registering {
    inputs.files(publishingDefaults, publishingFile, iconFile, manifestTemplate).optional()
    inputs.files(fileTree(gameIconDir))
    outputs.dir(generatedPublishing)
    doLast {
        val output = generatedPublishing.get().asFile
        output.deleteRecursively()
        output.resolve("assets").mkdirs()
        output.resolve("res").mkdirs()
        if (managed) output.resolve("assets/publishing.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(publishingConfig)))
        if (managed && iconFile.exists()) {
            output.resolve("res/drawable-nodpi").mkdirs()
            iconFile.copyTo(output.resolve("res/drawable-nodpi/publishing_icon.png"), overwrite = true)
        }
        fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        val games = (publishingConfig["games"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
        val aliases = linkedMapOf<String, String>()
        val manifestAliases = StringBuilder()
        val labels = StringBuilder("<resources>\n")
        for (game in games) {
            val id = game["id"] as? String ?: continue
            if (!Regex("[a-zA-Z][a-zA-Z0-9-]{1,39}").matches(id) || aliases.containsKey("game:$id")) continue
            val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val alias = "com.example.myapplication.MainActivityGame${hash}Alias"
            val resource = "publishing_game_$hash"
            val iconName = game["iconFile"] as? String ?: ""
            val dedicatedIcon = if (Regex("[0-9a-f]{64}\\.png").matches(iconName)) gameIconDir.resolve(iconName) else null
            val icon = if (dedicatedIcon?.isFile == true) {
                output.resolve("res/drawable-nodpi").mkdirs()
                dedicatedIcon.copyTo(output.resolve("res/drawable-nodpi/$resource.png"), overwrite = true)
                "@drawable/$resource"
            } else if (managed && iconFile.exists()) "@drawable/publishing_icon" else "@mipmap/ic_launcher"
            val label = (game["name"] as? String ?: id).replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
            labels.append("<string name=\"$resource\" formatted=\"false\">${xml("\"$label\"")}</string>\n")
            aliases["game:$id"] = alias
            manifestAliases.append("""
                <activity-alias android:name="$alias" android:enabled="false" android:exported="true"
                    android:icon="$icon" android:roundIcon="$icon" android:label="@string/$resource"
                    android:targetActivity=".MainActivity">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity-alias>
            """.trimIndent())
        }
        labels.append("</resources>")
        output.resolve("res/values").mkdirs()
        output.resolve("res/values/publishing_games.xml").writeText(labels.toString())
        output.resolve("assets/publishing-launchers.json").writeText(JsonOutput.toJson(aliases))
        output.resolve("AndroidManifest.xml").writeText(
            manifestTemplate.readText().replace("</application>", "$manifestAliases\n</application>")
        )
    }
}
tasks.named("preBuild").configure { dependsOn(generatePublishing) }

val keystorePropertiesFile = rootProject.file(System.getenv("H5_RELEASE_SIGNING_PROPERTIES") ?: "keystore.properties")
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
        manifest.srcFile(generatedPublishing.map { it.file("AndroidManifest.xml") })
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
            isDebuggable = false
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
