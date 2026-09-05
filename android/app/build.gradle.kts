import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.ksp)
	alias(libs.plugins.hilt)
}

/** White-label values come from gradle.properties so a rebrand never touches the code. */
fun prop(name: String, fallback: String = ""): String =
	providers.gradleProperty(name).orNull ?: fallback

/** Release signing is optional: without keystore.properties the release build stays unsigned. */
val keystoreProperties = Properties().apply {
	val file = rootProject.file("keystore.properties")
	if (file.exists()) file.inputStream().use { load(it) }
}

android {
	namespace = "pro.bixplayer.player"
	// Several AndroidX artifacts now require compiling against 37; targetSdk stays at 36
	// as planned, which is the supported and recommended combination.
	compileSdk = 37

	defaultConfig {
		applicationId = prop("bix.applicationId", "pro.bixplayer.player")
		minSdk = 23
		targetSdk = 36
		versionCode = prop("bix.versionCode", "1").toInt()
		versionName = prop("bix.versionName", "1.0.0")
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		resValue("string", "app_name", prop("bix.appName", "Bix Player"))
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
		debug {
			applicationIdSuffix = ".debug"
			isMinifyEnabled = false
			buildConfigField("String", "API_BASE_URL", "\"${prop("bix.apiBaseUrl.debug", "http://10.0.2.2:8000/")}\"")
			buildConfigField("boolean", "NETWORK_LOGGING", "true")
		}
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			buildConfigField("String", "API_BASE_URL", "\"${prop("bix.apiBaseUrl.release", "https://bixplayer.pro/")}\"")
			buildConfigField("boolean", "NETWORK_LOGGING", "false")
			if (keystoreProperties.isNotEmpty()) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
	}

	buildFeatures {
		compose = true
		buildConfig = true
		resValues = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
		isCoreLibraryDesugaringEnabled = true
	}

	packaging {
		resources.excludes += setOf(
			"/META-INF/{AL2.0,LGPL2.1}",
			"/META-INF/DEPENDENCIES",
			"/META-INF/LICENSE*",
		)
	}

	testOptions {
		unitTests {
			isReturnDefaultValues = true
			all { it.testLogging { events("failed", "skipped") } }
		}
	}

	lint {
		warningsAsErrors = false
		abortOnError = true
		disable += setOf("GradleDependency", "ObsoleteLintCustomCheck")
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}

ksp {
	arg("room.schemaLocation", "$projectDir/schemas")
	arg("room.generateKotlin", "true")
}

dependencies {
	coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.coroutines.android)
	implementation(libs.timber)

	// Compose. TV screens use tv-material; shared components use foundation/material3.
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.foundation)
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.compose.material3)
	implementation(libs.compose.material.icons)
	implementation(libs.tv.material)
	implementation(libs.tv.foundation)
	debugImplementation(libs.compose.ui.tooling)

	implementation(libs.navigation.compose)

	implementation(libs.hilt.android)
	ksp(libs.hilt.compiler)
	implementation(libs.hilt.navigation.compose)
	implementation(libs.hilt.work)
	ksp(libs.hilt.work.compiler)

	implementation(libs.room.runtime)
	implementation(libs.room.ktx)
	implementation(libs.room.paging)
	ksp(libs.room.compiler)

	implementation(libs.datastore.preferences)
	implementation(libs.work.runtime.ktx)
	implementation(libs.paging.runtime)
	implementation(libs.paging.compose)

	implementation(libs.retrofit)
	implementation(libs.retrofit.moshi)
	implementation(libs.moshi)
	ksp(libs.moshi.kotlin.codegen)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging)

	implementation(libs.coil.compose)
	implementation(libs.coil.network.okhttp)
	implementation(libs.zxing.core)

	implementation(libs.media3.exoplayer)
	implementation(libs.media3.exoplayer.hls)
	implementation(libs.media3.ui)
	implementation(libs.media3.session)

	testImplementation(libs.junit)
	testImplementation(libs.coroutines.test)
	testImplementation(libs.mockwebserver)
	testImplementation(libs.turbine)
	testImplementation(libs.truth)
	testImplementation(libs.room.testing)
}
