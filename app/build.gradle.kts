plugins {
    alias(libs.plugins.android.application)
}

val packagedResources = tasks.register<Sync>("prepareAppResources") {
    // Android's resource merger rejects the adjacent provenance JSON used by the design tooling.
    from("src/main/res") { exclude("**/*.webp.json") }
    into(layout.buildDirectory.dir("generated/packaged-resources/main"))
}

android {
    namespace = "com.haven.kegent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haven.kegent"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets.getByName("main").res.setSrcDirs(listOf(packagedResources))
}

tasks.named("preBuild") { dependsOn(packagedResources) }

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.register<Copy>("exportDebugApk") {
    group = "build"
    description = "Builds the installable kegent.apk debug package."
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("outputs/apk/kegent"))
    rename { "kegent.apk" }
}
