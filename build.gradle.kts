// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        // Explicit source roots: a "**" glob also walks build/, where KSP output can vanish mid-scan.
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.kts", "app/*.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
