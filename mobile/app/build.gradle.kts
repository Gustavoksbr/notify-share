import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// local.properties fica fora do controle de versao: e onde entram o
// GOOGLE_CLIENT_ID e as credenciais da chave de release (RELEASE_*).
val localProperties: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val googleClientId: String = localProperties.getProperty("GOOGLE_CLIENT_ID").orEmpty()

android {
    namespace = "com.notifyshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.notifyshare"
        // 26 e o piso: abaixo disso o NotificationListenerService e o modelo de
        // canais de notificacao mudam o suficiente para virar um caso a parte.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                // Chave de release propria (ver mobile/local.properties, fora do
                // controle de versao). O SHA-1 dela precisa estar cadastrado no
                // Firebase (Configuracoes do projeto > Suas apps > SHA) para o
                // login com Google funcionar neste build.
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            } else {
                // Sem RELEASE_STORE_FILE em local.properties (ex.: clone novo,
                // maquina de outro dev): cai na chave de debug so para o build
                // instalar e rodar localmente. NUNCA publique um APK/AAB assinado
                // assim numa loja — gere sua propria chave de release.
                val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            // Por padrao o aparelho fala com o PC pelo cabo: adb reverse
            // tcp:8080 tcp:8080 faz o localhost do celular chegar no backend
            // rodando aqui. Para apontar o debug para producao (ou o backend de
            // E2E), passe -PapiBaseUrl=https://notify-share.onrender.com/
            val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?)
                ?: "http://localhost:8080/"
            val wsUrl = (project.findProperty("wsUrl") as String?) ?: apiBaseUrl
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
                .trimEnd('/') + "/ws"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "WS_URL", "\"$wsUrl\"")
            // com.notifyshare.debug — convive com o release (com.notifyshare) no
            // mesmo aparelho. Nome e icone distintos para nao confundir.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Notify Share DEV")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            // Mantem o applicationId "com.notifyshare" (o que esta no
            // google-services.json). Instala junto do dev sem colisao.
            resValue("string", "app_name", "Notify Share")
            buildConfigField("String", "API_BASE_URL", "\"https://notify-share.onrender.com/\"")
            buildConfigField("String", "WS_URL", "\"wss://notify-share.onrender.com/ws\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
