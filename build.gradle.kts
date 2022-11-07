import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.7.20"
    id("io.github.lhotari.gradle-nar-plugin") version "0.5.1"
    `maven-publish`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

group = "io.github.endzeitbegins"

val niFiVersion = "1.18.0"

dependencies {
    // Apache NiFi
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("com.natpryce:hamkrest:1.8.0.1")
    testImplementation("org.apache.nifi:nifi-mock:$niFiVersion")
    testImplementation("org.apache.mina:mina-core:2.2.1")
    testImplementation("org.slf4j:slf4j-simple:2.0.3")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
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

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}