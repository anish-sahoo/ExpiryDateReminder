plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    // Generated sources are excluded through .editorconfig rather than a ktlint filter here:
    // the filter closure captures Project, which the configuration cache rejects.

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }
}

/**
 * The gate CI runs, and the one to run locally before pushing.
 *
 * Ordered cheapest-first so a formatting slip fails in seconds rather than after a full
 * Android build. `ktlintCheck` only verifies; `./gradlew ktlintFormat` fixes what it can.
 */
tasks.register("ciCheck") {
    group = "verification"
    description = "Formatting, static analysis, unit tests and a debug build."
    dependsOn(
        "ktlintCheck",
        "detekt",
        // The KMP module has no plain `test` task; its unit tests are the Android host tests.
        ":shared:testAndroid",
        ":composeApp:testDebugUnitTest",
        ":composeApp:assembleDebug",
        // Android Lint catches a different class of problem from ktlint and detekt: invalid
        // resources, manifest mistakes, unsafe API levels. It is what failed the first
        // release build, long after the code itself was "clean".
        ":composeApp:lintDebug",
    )
}

/**
 * The same gate plus the instrumented suite, which needs a device or emulator attached.
 * Kept separate from [ciCheck] so the common case stays device-free and fast.
 */
tasks.register("ciCheckDevice") {
    group = "verification"
    description = "Everything ciCheck runs, plus the on-device end-to-end tests."
    dependsOn("ciCheck", ":composeApp:connectedDebugAndroidTest")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
