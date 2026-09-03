allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}

// Some plugins (e.g. permission_handler_android) pin compileSdk to a value
// that may not be installed locally. Force every Android subproject to build
// against the same compileSdk as the app itself to avoid a missing-platform
// build failure. Must be registered before evaluationDependsOn(":app") below
// forces early evaluation of the app project.
subprojects {
    afterEvaluate {
        if (project.plugins.hasPlugin("com.android.library") ||
            project.plugins.hasPlugin("com.android.application")
        ) {
            configure<com.android.build.gradle.BaseExtension> {
                compileSdkVersion(36)
            }
        }
    }
}

subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
