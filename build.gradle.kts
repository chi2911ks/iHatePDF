// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "com.github.chi2911ks.iHatePDF"
    version = System.getenv("VERSION") ?: "1.0.0-SNAPSHOT"
}
