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
    implementation("org.apache.nifi:nifi-mock:$niFiVersion")
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