import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// local.properties fica fora do controle de versao: e onde entram o
// GOOGLE_CLIENT_ID e as credenciais da chave de release (RELEASE_*).
val localProperties: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val googleClientId: String = localProperties.getProperty("GOOGLE_CLIENT_ID").orEmpty()
// Client "Android" (nao o Web) — o unico tipo que aceita redirect com esquema
// customizado (com.notifyshare:/...). Usado so no fallback de login pelo
// navegador (GoogleWebAuth), quando o Credential Manager nao acha conta no
// aparelho. Pegue o "ID do cliente" na pagina desse client no Google Cloud
// Console (Credenciais > o client tipo Android usado no release).
val googleAndroidClientId: String = localProperties.getProperty("GOOGLE_ANDROID_CLIENT_ID").orEmpty()

// .env fica fora do controle de versao: ajustes de desenvolvimento que o dev
// muda livremente sem mexer em build.gradle.kts (ver .env.example).
val dotEnv: Map<String, String> = run {
    val f = rootProject.file(".env")
    if (!f.exists()) emptyMap() else f.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains('=') }
        .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
}

/** Itens por página do cofre ("Salvos") quando não há filtro ativo. Vazio/ausente = 100. */
val vaultPageSize: String = dotEnv["VAULT_PAGE_SIZE"].orEmpty()

android {
    namespace = "com.notifyshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.notifyshare"
        // 26 e o piso: abaixo disso o NotificationListenerService e o modelo de
        // canais de notificacao mudam o suficiente para virar um caso a parte.
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
        buildConfigField("String", "VAULT_PAGE_SIZE", "\"$vaultPageSize\"")

        // AppAuth registra sozinho, via manifest merge, uma activity que recebe
        // esse esquema de volta do navegador (com.notifyshare:/oauth2redirect) —
        // e o fallback do login Google quando não há conta no aparelho.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.notifyshare"

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
    implementation(libs.firebase.crashlytics)
    // Analytics: da ao Crashlytics a trilha de eventos antes do crash (breadcrumbs).
    implementation(libs.firebase.analytics)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)
    implementation(libs.openid.appauth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
