dependencies {
    compileOnly(libs.annotation)
}

extension {
    name = "extensions/claude.mpe"
}

android {
    namespace = "app.morphe.extension.claude"

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
