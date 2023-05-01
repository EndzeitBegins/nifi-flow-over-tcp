import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.narPlugin)
    signing
    `maven-publish`
    alias(libs.plugins.publishPlugin)
}

repositories {
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

group = "io.github.endzeitbegins"
val artifactName = "nifi-flow-over-tcp"

java {
    toolchain {
        languageVersion.set(
            libs.versions.java.map { version -> JavaLanguageVersion.of(version) }
        )
    }
}

kotlin {
    explicitApi()
}

dependencies {
    // Apache NiFi
    implementation(libs.nifi.api)
    implementation(libs.nifi.utils)
    // former nifi-processor-utils - see https://issues.apache.org/jira/browse/NIFI-9610
    implementation(libs.nifi.eventListen)
    implementation(libs.nifi.eventPut)

    // - NAR dependency
    parentNar(libs.nifi.standardServicesApiNar)

    // JSON (de)serialization
    implementation(libs.kotlinx.serialization.json)
}

testing {
    @Suppress("UnstableApiUsage")
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(libs.nifi.mock)
                implementation.bundle(libs.bundles.strikt)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.slf4j.simple)
            }
        }

        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            testType.set(TestSuiteType.INTEGRATION_TEST)

            sources {
                resources {
                    this.srcDirs
                }
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)

                        environment("nifi.version", libs.versions.nifi.get())
                    }
                }
            }

            dependencies {
                // include build .nar artifact to integrate into the Docker container
                implementation(tasks.nar.map { dependencyFactory.create(it.outputs.files) })

                implementation.bundle(libs.bundles.strikt)
                implementation.bundle(libs.bundles.ktor)
                implementation(libs.testing.testcontainers)
                implementation(libs.slf4j.simple)
            }
        }
    }
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

        artifact(tasks.nar)

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
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

tasks {
    withType<Test>().configureEach {
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
                    <systemApiVersion>${libs.versions.nifi.get()}</systemApiVersion>${
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

fun <P: ExternalDependency> DependencyHandlerScope.parentNar(parentNarDependency: Provider<P>) {
    nar(parentNarDependency)
    testImplementation(parentNarDependency)
}
