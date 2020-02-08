import de.marcphilipp.gradle.nexus.NexusPublishExtension
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

plugins {
    id("de.marcphilipp.nexus-publish") version "0.4.0"
    signing
}

val nameRegex = Regex("capnproto-c\\+\\+-(.+?)\\.tar\\.gz")
val capnProtoTarGzMatch = Files.list(project.projectDir.toPath()).use { files ->
    files.asSequence()
        .map { p -> p.fileName.toString() }
        .mapNotNull { nameRegex.matchEntire(it) }
        .singleOrNull()
}

val njobs = Runtime.getRuntime().availableProcessors()
val capnProtoDir = project.projectDir.resolve("capnproto-c++-${capnProtoTarGzMatch?.groupValues?.get(1)}")
val capnProtoJavaDir = project.projectDir.resolve("capnproto-java-master")

val taskGroup = "Cap'n Proto"

inline fun Project.execIn(
    workingDir: File,
    crossinline block: ExecSpec.() -> Unit
) = exec {
    this.workingDir = workingDir
    block()
}

val installCapnProto = tasks.register("installCapnProto") {
    group = taskGroup
    description = "Install Cap'n Proto into the current system"

    doLast {
        execIn(capnProtoDir) {
            commandLine("./configure")
        }
        execIn(capnProtoDir) {
            commandLine("make", "clean")
        }
        execIn(capnProtoDir) {
            commandLine("make", "-j$njobs")
        }
        execIn(capnProtoDir) {
            commandLine("make", "install")
        }
    }
}

val buildCapnProto = tasks.register("buildCapnProto") {
    group = taskGroup
    description = "Builds Cap'n Proto for packaging"
    dependsOn(installCapnProto)

    val outputDirectory = project.buildDir.resolve("capnproto")

    inputs.dir(capnProtoDir)
    outputs.dir(outputDirectory)

    doLast {
        Files.createDirectories(outputDirectory.toPath())
        execIn(capnProtoDir) {
            commandLine("./configure", "--with-external-capnp", "--prefix=$outputDirectory")
        }
        execIn(capnProtoDir) {
            commandLine("make", "clean")
        }
        execIn(capnProtoDir) {
            commandLine("make", "-j$njobs")
        }
        execIn(capnProtoDir) {
            commandLine("make", "install")
        }
    }
}

val buildCapnProtoJava = tasks.register("buildCapnProtoJava") {
    group = taskGroup
    description = "Builds Cap'n Proto Java for packaging"
    dependsOn(installCapnProto)

    val outputDirectory = project.buildDir.resolve("capnproto-java")

    inputs.dir(capnProtoJavaDir)
    outputs.dir(outputDirectory)

    doLast {
        Files.createDirectories(outputDirectory.toPath())
        execIn(capnProtoJavaDir) {
            commandLine("make", "clean")
        }
        execIn(capnProtoJavaDir) {
            commandLine("make", "-j$njobs")
        }
        Files.move(
            capnProtoJavaDir.resolve("capnpc-java").toPath(),
            outputDirectory.resolve("capnpc-java").toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

val capnProtoJar = tasks.register<Jar>("capnProtoJar") {
    group = taskGroup
    description = "Creates a JAR from Cap'n Proto build"
    from(buildCapnProto)

    archiveBaseName.set("capnproto-exec")
    archiveClassifier.set("linux")
}

val capnProtoJavaJar = tasks.register<Jar>("capnProtoJavaJar") {
    group = taskGroup
    description = "Creates a JAR from Cap'n Proto Java build"
    from(buildCapnProtoJava)

    archiveBaseName.set("capnproto-java-exec")
    archiveClassifier.set("linux")
}

publishing {
    publications {
        register<MavenPublication>("capnproto") {
            pom {
                name.set("Cap'n Proto Executables")
            }
            groupId = "net.octyl.capnproto"
            artifactId = "capnproto-exec"

            capnProtoTarGzMatch?.let {
                version = capnProtoTarGzMatch.groupValues[1]
            }
            artifact(capnProtoJar.get())
        }
        register<MavenPublication>("capnproto-java") {
            pom {
                name.set("Cap'n Proto Java Executables")
            }
            groupId = "net.octyl.capnproto"
            artifactId = "capnproto-java-exec"

            version = "0.1.5-SNAPSHOT"
            artifact(capnProtoJavaJar.get())
        }
    }
}

project.configure<SigningExtension> {
    // Only sign if it's possible.
    if (this.signatories.getDefaultSignatory(project) != null) {
        sign(project.extensions.getByType<PublishingExtension>()
            .publications.getByName("capnproto"))
        sign(project.extensions.getByType<PublishingExtension>()
            .publications.getByName("capnproto-java"))
    }
}

project.configure<NexusPublishExtension> {
    repositories {
        create("nexus") {
            nexusUrl.set(uri("https://oss.sonatype.org/service/local/"))
            username.set(project.provider {
                val usernameOptions = listOf(
                    project.findProperty("ossrhUsername") as? String
                )
                usernameOptions
                    .filterNot { it.isNullOrBlank() }
                    .firstOrNull()
            })
            password.set(project.provider {
                val passwordOptions = listOf(
                    project.findProperty("ossrhPassword") as? String,
                    System.getenv("OSSRH_PASSWORD")
                )
                passwordOptions
                    .filterNot { it.isNullOrBlank() }
                    .firstOrNull()
            })
        }
    }
}

