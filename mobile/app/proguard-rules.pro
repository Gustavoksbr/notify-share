# Regras extras para o release com minify ligado. As bibliotecas (AndroidX,
# Compose, Retrofit, OkHttp, Firebase) ja trazem suas proprias consumer-rules;
# o que fica aqui e so o que e especifico deste app.

# kotlinx.serialization: cada @Serializable gera um "$$serializer" e um
# metodo Companion.serializer() por reflexao de bytecode. Sem manter isso, o
# parse dos DTOs falha em runtime so no build de release (o debug, sem
# ofuscacao, nunca pega esse tipo de erro). Receita oficial da biblioteca.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class com.notifyshare.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.notifyshare.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.notifyshare.data.remote.**$$serializer { *; }

# WorkManager (WatchdogWorker) usa Room por baixo dos panos. A classe gerada
# WorkDatabase_Impl e localizada em runtime por reflexao (nome da classe +
# construtor sem argumentos), fora do alcance das regras normais de manter
# classes anotadas — sem isto o app crasha JA NO STARTUP, so no release:
# "NoSuchMethodException: WorkDatabase_Impl.<init>". Confirmado num teste
# real de release com minify (dispositivo fisico).
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl { <init>(); }

# Firebase: os SDKs (Crashlytics, Messaging, Analytics) se registram por
# "ComponentRegistrar" — o AndroidManifest lista os nomes das classes e o
# Firebase as instancia por reflexao, com o construtor sem argumentos. O R8
# em full mode remove esse construtor achando que ninguem chama, e o app
# crasha JA NO BOOT do release: "NoSuchMethodException:
# CrashlyticsRegistrar.<init>" / "FirebaseCrashlytics component is not
# present". Confirmado em dispositivo fisico.
-keepnames class * implements com.google.firebase.components.ComponentRegistrar
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Retrofit/OkHttp: sem isso o proxy dinamico da interface da API perde a
# assinatura generica dos "suspend fun" e o Retrofit nao consegue montar a
# call. Tambem cobre os avisos de classes opcionais que a OkHttp referencia
# mas o app nao usa (Conscrypt/BouncyCastle/OpenJSSE).
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
