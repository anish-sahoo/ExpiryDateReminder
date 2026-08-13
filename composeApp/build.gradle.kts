import java.util.Properties

plugins {
    // AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android is gone.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Signing credentials live outside version control. Absent keystore.properties the
// release build simply stays unsigned rather than failing the whole configuration,
// so contributors without the key can still build and test.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

android {
    namespace = "com.anish.expirydatereminder"
    compileSdk = 37

    // :shared's fake repositories, compiled into this module's unit tests rather than
    // duplicated. A KMP module cannot publish test fixtures to a consumer, and two copies of
    // a fake drift apart the moment one of the repository interfaces changes.
    sourceSets.getByName("test").kotlin.srcDir("../shared/src/commonTest/kotlin/com/anish/expirydatereminder/testing")

    lint {
        // Release builds already run lintVital, which is where the invalid backup rules and
        // the missing proguard file surfaced. Running the same checks on debug means they
        // surface on an ordinary build instead of the day of a release.
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        // The reports are far more useful than the console when something trips.
        htmlReport = true
        xmlReport = true
        // Translations are reviewed separately and deliberately lag the default locale.
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }

    testOptions {
        unitTests {
            // Robolectric needs real resources to inflate anything, and NotificationCopy
            // reads plurals straight out of them.
            isIncludeAndroidResources = true
        }
    }

    defaultConfig {
        applicationId = "com.anish.expirydatereminder"
        minSdk = 31
        targetSdk = 36

        // Live Play Store release is versionCode 6 / versionName "1.5".
        versionCode = 7
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en", "en-rGB", "de", "fr", "es", "it", "nl", "pt", "pl")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.coil.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.genai.prompt)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    // Lets the migration and settings tests stand up a real, in-memory schema instead of
    // guessing at what SQLite would have done.
    testImplementation(libs.sqldelight.android.driver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Supplies the empty activity the test host launches into.
    debugImplementation(libs.compose.ui.test.manifest)
}

// ---- ktlint for this module -----------------------------------------------------------
//
// The ktlint Gradle plugin finds its sources through the Kotlin Android plugin's extension.
// AGP 9 has Kotlin built in, so `org.jetbrains.kotlin.android` is no longer applied here and
// the plugin registers nothing but the `.kts` script tasks — meaning every screen, view model
// and widget in this module went unchecked while :shared was clean.
//
// Driving the ktlint CLI directly sidesteps the detection problem entirely, and reads the same
// .editorconfig, so both modules are held to one standard.
val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli(libs.ktlint.cli)
}

val ktlintSources = listOf("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    .map { "$it/**/*.kt" }

fun Project.registerKtlint(name: String, format: Boolean) = tasks.register<JavaExec>(name) {
    group = "verification"
    description = if (format) "Formats Kotlin in :composeApp." else "Checks Kotlin formatting in :composeApp."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    // Without this, ktlint's own use of reflection trips the module system on JDK 21.
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args = buildList {
        if (format) add("--format")
        addAll(ktlintSources)
    }
    workingDir = projectDir
}

val ktlintAppCheck = registerKtlint("ktlintAppCheck", format = false)
val ktlintAppFormat = registerKtlint("ktlintAppFormat", format = true)

// Hooked onto the names everyone already runs, so nobody has to know this module is special.
tasks.named("ktlintCheck") { dependsOn(ktlintAppCheck) }
tasks.named("ktlintFormat") { dependsOn(ktlintAppFormat) }
