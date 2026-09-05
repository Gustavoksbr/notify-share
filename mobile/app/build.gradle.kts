import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Client ID "Web application" do Google, para o login com Google. Fora do
// controle de versao: fica em local.properties (GOOGLE_CLIENT_ID=...).
val googleClientId: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("GOOGLE_CLIENT_ID").orEmpty()

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
        // O build de "release" (que aponta para producao) precisa estar assinado
        // para instalar. Por ora usa a MESMA chave de debug: assim o SHA-1 nao
        // muda e o login com Google segue funcionando sem mexer no Firebase.
        // Trocar por uma chave de release propria antes de publicar numa loja.
        create("release") {
            val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
            isMinifyEnabled = false
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
