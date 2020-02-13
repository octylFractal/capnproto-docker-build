import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

plugins {
    id("com.jfrog.bintray") version "1.8.4"
    `maven-publish`
    signing
}

val nameRegex = Regex("capnproto-c\\+\\+-(.+?)\\.tar\\.gz")
val capnProtoTarGzMatch = Files.list(Paths.get("/")).use { files ->
    files.asSequence()
        .map { p -> p.fileName.toString() }
        .mapNotNull { nameRegex.matchEntire(it) }
        .singleOrNull()
}

var buildStr = "build." + (project.findProperty("build.number") ?: "dev")

capnProtoTarGzMatch?.let {
    version = "${capnProtoTarGzMatch.groupValues[1]}+$buildStr"
}
val njobs = Runtime.getRuntime().availableProcessors()
val capnProtoDir = File("/capnproto-c++-${capnProtoTarGzMatch?.groupValues?.get(1)}")
val capnProtoJavaDir = File("/capnproto-java")

val taskGroup = "Cap'n Proto"

inline fun Project.execIn(
    workingDir: File,
    crossinline block: ExecSpec.() -> Unit
) = exec {
    require(Files.exists(workingDir.toPath())) { "No such directory: $workingDir" }
    this.workingDir = workingDir
    block()
}

val installCapnProto = tasks.register("installCapnProto") {
    group = taskGroup
    description = "Install Cap'n Proto into the current system"

    doLast {
        execIn(capnProtoDir) {
            commandLine(capnProtoDir.resolve("./configure").absolutePath)
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
            commandLine(capnProtoDir.resolve("./configure").absolutePath,
                "--with-external-capnp",
                "--prefix=${outputDirectory.absolutePath}")
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
        copy {
            from(capnProtoJavaDir.resolve("capnpc-java"))
            into(outputDirectory.resolve("bin"))
        }
        copy {
            from(capnProtoJavaDir.resolve("compiler/src/main/schema"))
            into(outputDirectory.resolve("include"))
        }
    }
}

val capnProtoJar = tasks.register<Jar>("capnProtoJar") {
    group = taskGroup
    description = "Creates a JAR from Cap'n Proto build"
    from(buildCapnProto)

    archiveBaseName.set("capnproto-exec")
    archiveClassifier.set("linux-x86_64")
}

val capnProtoJavaJar = tasks.register<Jar>("capnProtoJavaJar") {
    group = taskGroup
    description = "Creates a JAR from Cap'n Proto Java build"
    from(buildCapnProtoJava)

    archiveBaseName.set("capnproto-java-exec")
    archiveClassifier.set("linux-x86_64")
}

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")
    setPublications("capnproto", "capnproto-java")
    with(pkg) {
        repo = "unofficial-capn-proto-executables"
        name = "capnproto-executables"
        userOrg = "octylfractal"
        setLicenses("MIT")
        vcsUrl = "https://github.com/octylFractal/capnproto-docker-build.git"
        with(version) {
            name = project.version.toString()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("capnproto") {
            pom {
                name.set("Cap'n Proto Executables")
            }
            groupId = "net.octyl.capnproto"
            artifactId = "capnproto-exec"
            version = project.version.toString()

            artifact(capnProtoJar.get())
        }
        register<MavenPublication>("capnproto-java") {
            pom {
                name.set("Cap'n Proto Java Executables")
            }
            groupId = "net.octyl.capnproto"
            artifactId = "capnproto-java-exec"

            val cap = java.io.ByteArrayOutputStream()
            if (Files.exists(capnProtoJavaDir.toPath())) {
                execIn(capnProtoJavaDir) {
                    commandLine("git", "rev-parse", "--short", "--verify", "HEAD")
                    standardOutput = cap
                }
            }
            val gitId = cap.toString(StandardCharsets.UTF_8).trim().ifBlank { "dev" }

            version = "0.1.5-$gitId+$buildStr"
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
