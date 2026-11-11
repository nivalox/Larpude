group = "app.morphe"

patches {
    about {
        name = "Larpude"
        description = "Adds a Larpude entry to the Claude app."
        source = "https://github.com/nivalox/Larpude"
        author = "nivalox"
        contact = "https://github.com/nivalox/Larpude/issues"
        website = "https://github.com/nivalox/Larpude"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    compileOnly(libs.morphe.patcher)

    // Used by the plugin's patch list generator.
    implementation(libs.gson)

    // Required due to smali, or the build fails.
    implementation(libs.guava)

    // Android API stubs.
    compileOnly(project(":patches:stub"))
}

// Writes ../patches-list.json (the patch catalog Morphe Manager reads) from the built .mpp.
tasks.register<JavaExec>("generatePatchesList") {
    description = "Generate patches-list.json from the built patch bundle"
    dependsOn(tasks.named("jar"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.morphe.util.PatchListGeneratorKt")
    args(project.version.toString())
}
