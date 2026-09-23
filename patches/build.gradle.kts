group = "com.instalume"

patches {
    about {
        name = "InstaLume Patches"
        description = "InstaLume V1.0.0 by Umaiz Sufiyan: premium Instagram customization with Liquid Glass UI, modular features, privacy controls, backup/restore, and version-aware patching. Independent project, not affiliated with Meta/Instagram. GPLv3, based on FeurStagram (GPLv3) with Morphe/Piko attribution in NOTICE."
        source = "https://github.com/sufiyan-sabeel/InstaLume.git"
        author = "Umaiz Sufiyan"
        contact = "https://github.com/sufiyan-sabeel/InstaLume/issues"
        website = "https://github.com/sufiyan-sabeel/InstaLume"
        license = "GPLv3"
    }
}

dependencies {
    // Provides app.morphe.util.* helpers (DOM, bytecode, references) used by the patches.
    implementation(libs.morphe.patches.library)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
