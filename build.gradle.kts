import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    java
    kotlin("jvm") version "1.8.10"
    id("io.github.lhotari.gradle-nar-plugin") version "0.5.1"
    signing
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

repositories {
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

group = "io.github.endzeitbegins"
val artifactName = "nifi-flow-over-tcp"

val niFiVersion = "1.20.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

kotlin {
    explicitApi()
}

testing {

}

dependencies {
    // Apache NiFi
    implementation("org.apache.nifi:nifi-api:$niFiVersion")
    implementation("org.apache.nifi:nifi-utils:$niFiVersion")
    // former nifi-processor-utils - see https://issues.apache.org/jira/browse/NIFI-9610
    implementation("org.apache.nifi:nifi-event-listen:$niFiVersion")
    implementation("org.apache.nifi:nifi-event-put:$niFiVersion")

    // - NAR dependency
    parentNar("org.apache.nifi:nifi-standard-services-api-nar:$niFiVersion")

    // JSON (de)serialization
    val jacksonVersion = "2.14.2"
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // utility libraries
    implementation("commons-net:commons-net:3.9.0")

    // testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.strikt:strikt-core:0.34.1")
    testImplementation("org.apache.nifi:nifi-mock:$niFiVersion")
    testImplementation("org.slf4j:slf4j-simple:2.0.6")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

signing {
    val signingKey = System.getenv("GPG_SIGNING_KEY")
    val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")

    useInMemoryPgpKeys(signingKey, signingPassword)

    sign(publishing.publications)
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = artifactName

        artifact(tasks["nar"])

        pom {
            name.set(artifactName)
            description.set("A Apache NiFi Archive (.nar) with Processor implementations for transmitting FlowFiles over bare TCP.")
            url.set("https://github.com/EndzeitBegins/nifi-flow-over-tcp")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            developers {
                developer {
                    id.set("endzeitbegins")
                    name.set("EndzeitBegins")
                    email.set("io.github.endzeitbegins@gmail.com")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/EndzeitBegins/nifi-flow-over-tcp.git")
                developerConnection.set("scm:git:ssh://github.com/EndzeitBegins/nifi-flow-over-tcp.git")
                url.set("https://github.com/EndzeitBegins/nifi-flow-over-tcp")
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

tasks {
    test {
        useJUnitPlatform()

        testLogging {
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL

            events(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
        }
    }

    nar {
        // manual workaround to fix issue https://github.com/lhotari/gradle-nar-plugin/issues/2

        // 1. write extension-manifest.xml
        val extensionManifestFile = file("extension-manifest.xml")
        doFirst {
            val parentNarDependency: Dependency? = parentNarConfiguration?.allDependencies?.singleOrNull()

            val extensionManifestContent = """
                <extensionManifest>
                    <groupId>$group</groupId>
                    <artifactId>$artifactName</artifactId>
                    <version>${project.version}</version>
                    <systemApiVersion>${niFiVersion}</systemApiVersion>${
                    if (parentNarDependency != null) """
                        <parentNar>
                            <groupId>${parentNarDependency.group}</groupId>
                            <artifactId>${parentNarDependency.name}</artifactId>
                            <version>${parentNarDependency.version}</version>
                        </parentNar>        
                    """.trimIndent() else ""
            }<extensions/>
                </extensionManifest>
            """.trimIndent()

            extensionManifestFile.writeText(extensionManifestContent)
        }
        from(extensionManifestFile) {
            into("META-INF/docs/")
        }
        doLast {
            extensionManifestFile.delete()
        }

        // 2. add required attributes to MANIFEST.MF
        manifest {
            attributes(
                "Build-Timestamp" to "${Instant.now().truncatedTo(ChronoUnit.SECONDS)}",
                "Clone-During-Instance-Class-Loading" to "false",
            )
        }
    }
}

fun DependencyHandlerScope.parentNar(parentNarDependency: String) {
    nar(parentNarDependency)
    testImplementation(parentNarDependency)
}