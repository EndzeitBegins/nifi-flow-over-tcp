import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.7.21"
    id("io.github.lhotari.gradle-nar-plugin") version "0.5.1"
    signing
    `maven-publish`
}

repositories {
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

group = "io.github.endzeitbegins"


kotlin {
    explicitApi()
}

dependencies {
    // Apache NiFi
    val niFiVersion = "1.18.0"
    implementation("org.apache.nifi:nifi-api:$niFiVersion")
    implementation("org.apache.nifi:nifi-utils:$niFiVersion")
    // former nifi-processor-utils - see https://issues.apache.org/jira/browse/NIFI-9610
    implementation("org.apache.nifi:nifi-event-listen:$niFiVersion")
    implementation("org.apache.nifi:nifi-listed-entity:$niFiVersion")
    // - NAR dependencies
    implementation("org.apache.nifi:nifi-standard-services-api-nar:$niFiVersion")

    // utility libraries
    implementation("commons-net:commons-net:3.8.0")

    // testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    testImplementation("com.natpryce:hamkrest:1.8.0.1")
    testImplementation("org.apache.nifi:nifi-mock:$niFiVersion")
    testImplementation("org.slf4j:slf4j-simple:2.0.3")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
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
        artifactId = "nifi-flow-over-tcp"

        artifact(tasks["nar"])

        pom {
            name.set("nifi-flow-over-tcp")
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

    repositories {
        maven {
            name = "OSSRH"

            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if ("$version".endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

tasks {
    val jvmTargetVersion = "1.8"

    withType<JavaCompile> {
        sourceCompatibility = jvmTargetVersion
        targetCompatibility = jvmTargetVersion
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = jvmTargetVersion
        }
    }

    test {
        useJUnitPlatform()
    }
}
