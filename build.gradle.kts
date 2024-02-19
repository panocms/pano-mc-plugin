val vertxVersion = "4.3.7"

plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("kapt") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = "com.panomc.plugins"
version =
    (if (project.hasProperty("version") && project.findProperty("version") != "unspecified") project.findProperty("version") else "local-build")!!

val buildType = project.findProperty("buildType") as String? ?: "alpha"
val timeStamp: String by project
val buildDir by extra { file("${rootProject.layout.buildDirectory.get()}/libs") }

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://oss.sonatype.org/content/repositories/iovertx-3720/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // spigot
    compileOnly("org.spigotmc:spigot-api:1.19.2-R0.1-SNAPSHOT")

    // bungeecord
    compileOnly("net.md-5:bungeecord-api:1.19-R0.1-SNAPSHOT")

    // paper spigot
    compileOnly("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")

    // velocity
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.1")

    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-config:$vertxVersion")
    implementation("io.vertx:vertx-config-hocon:$vertxVersion")
    implementation("io.vertx:vertx-json-schema:$vertxVersion")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.1")


    // dagger 2x
    implementation("com.google.dagger:dagger:2.44.2")
    kapt("com.google.dagger:dagger-compiler:2.44.2")

    implementation("org.springframework:spring-context:5.3.24")
}

tasks.named("jar").configure {
    enabled = false
}

tasks {
    register("copyJar") {
        doLast {
            copy {
                from(shadowJar.get().archiveFile.get().asFile.absolutePath)
                into("../minecraft test servers/Spigot/plugins")
            }

            copy {
                from(shadowJar.get().archiveFile.get().asFile.absolutePath)
                into("../minecraft test servers/Bungeecord/plugins")
            }

            copy {
                from(shadowJar.get().archiveFile.get().asFile.absolutePath)
                into("../minecraft test servers/Velocity/plugins")
            }
        }
    }

    build {
        dependsOn(shadowJar)
    }

    register("buildDev") {
        dependsOn("build")
    }

    // This task builds and copys jar into server folders for test
    register("buildPluginDev") {
        dependsOn("buildDev")
        dependsOn("copyJar")
    }

    shadowJar {
        manifest {
            val attrMap = mutableMapOf<String, String>()

            if (project.gradle.startParameter.taskNames.contains("buildDev"))
                attrMap["MODE"] = "DEVELOPMENT"

            attrMap["VERSION"] = version.toString()
            attrMap["BUILD_TYPE"] = buildType

            attributes(attrMap)
        }

        relocate("io.netty", "vertx.io.netty")

        archiveFileName.set("${rootProject.name}-${version}.jar")

        if (project.gradle.startParameter.taskNames.contains("publish")) {
            archiveFileName.set(archiveFileName.get().lowercase())
        }
    }
}

publishing {
    repositories {
        maven {
            name = "pano-mc-plugin"
            url = uri("https://maven.pkg.github.com/panocms/pano-mc-plugin")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME_GITHUB")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("TOKEN_GITHUB")
            }
        }
    }

    publications {
        create<MavenPublication>("shadow") {
            project.extensions.configure<com.github.jengelman.gradle.plugins.shadow.ShadowExtension> {
                artifactId = "pano-mc"
                component(this@create)
            }
        }
    }
}
