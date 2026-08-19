plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.ihatepdf.converter"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "pdf-word-converter"
                pom {
                    name.set("iHatePDF Android Converter")
                    description.set("Offline PDF to Word and Word to PDF converter for Android")
                    url.set("https://github.com/chi2911ks/iHatePDF")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.apache.poi.ooxml)
    implementation(libs.apache.poi.scratchpad)
    implementation(libs.pdfbox.android)
    implementation(libs.jp2.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
