plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9 requires this dedicated plugin for KMP modules; com.android.library is no
    // longer compatible with org.jetbrains.kotlin.multiplatform.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.anish.expirydatereminder.shared"
        compileSdk = 37
        minSdk = 31

        // Without this the KMP plugin builds no test compilation at all, so commonTest is
        // silently skipped: the parser and domain tests compile nowhere and run never.
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // `api` rather than `implementation`: the domain models expose LocalDate and
            // Flow in their public signatures, so consumers need them on the classpath.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

sqldelight {
    databases {
        create("EdrDatabase") {
            packageName.set("com.anish.expirydatereminder.db")
            // The legacy `itemsDatabase` file is never opened by SQLDelight, so its own
            // user_version of 8 is irrelevant here.
            verifyMigrations.set(true)
        }
    }
}
