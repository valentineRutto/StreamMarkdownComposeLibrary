plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

group = "io.github.valentinerutto"
version = "1.0.0"

mavenPublishing {
    coordinates("io.github.valentinerutto", "streammarkdowncompose", version.toString())

    publishToMavenCentral()
    signAllPublications()

    pom {
        name = "StreamMarkdownCompose"
        description = "A native Android streaming Markdown renderer for Jetpack Compose."
        inceptionYear = "2026"
        url = "https://github.com/valentineRutto/StreamMarkdownComposeLibrary"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "valentinerutto"
                name = "Valentine Rutto"
                url = "https://github.com/valentineRutto"
            }
        }

        scm {
            url = "https://github.com/valentineRutto/StreamMarkdownComposeLibrary"
            connection = "scm:git:git://github.com/valentineRutto/StreamMarkdownComposeLibrary.git"
            developerConnection = "scm:git:ssh://git@github.com/valentineRutto/StreamMarkdownComposeLibrary.git"
        }
    }
}

android {
    namespace = "com.valentinerutto.streammarkdowncompose"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}