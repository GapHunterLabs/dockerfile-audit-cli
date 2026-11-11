plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.gradleup.shadow")
}

group = "dev.gaphunter.dockerfileaudit"
version = "0.1.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

// Deliberately NOT applying the `application` plugin: this project only
// needs a runnable fat jar (`java -jar ...`), not `application`'s
// run/installDist/distZip tasks. Applying `application` alongside
// `com.gradleup.shadow` 8.3.5 on this Gradle version (9.5.0) crashes
// configuring the plugin's own generated `startShadowScripts`/
// `shadowDistTar` tasks ("You can't map a property that does not exist:
// propertyName=mainClassName") -- a real compatibility bug between the
// two plugins' application-integration path, not something fixable from
// this project's build file. The shadowJar sets Main-Class directly on
// its own manifest below, sidestepping that whole integration surface.
//
// The default `jar` task is disabled on purpose. With shadowJar's
// archiveClassifier set to "" (for a clean `dockerfile-audit-cli-0.1.0.jar`
// name), the thin `jar` task would otherwise write to that SAME path --
// whichever task ran last would win, and when the thin jar won the
// result was a stdlib-less jar that died at runtime with
// NoClassDefFoundError (kotlin/KotlinNothingValueException). This
// project ships only the fat jar, so there is no reason to produce the
// thin one at all; disabling it removes the output collision entirely.
tasks.jar {
    enabled = false
}

// A single, dependency-free fat jar is the whole point of this CLI: a
// GitHub Action step or a local `java -jar` invocation needs one file,
// no classpath assembly. shadowJar with no shaded dependencies (none
// exist here beyond the Kotlin stdlib) still bundles the stdlib itself,
// so the output jar runs on a bare JRE with no other setup.
tasks.shadowJar {
    archiveBaseName.set("dockerfile-audit-cli")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    manifest {
        attributes("Main-Class" to "dev.gaphunter.dockerfileaudit.MainKt")
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
