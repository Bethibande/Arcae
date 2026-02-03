import org.gradle.kotlin.dsl.withType
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.sequences.forEach

plugins {
    id("java")
    id("io.quarkus")
    id("org.openapi.generator") version "7.17.0"
}

group = "de.bethibande.repository"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://pckg.bethibande.com/repository/maven-releases/")
        name = "bethibande-releases"
    }
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-liquibase")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-hibernate-orm")

    implementation("io.quarkus:quarkus-scheduler")
    implementation("com.cronutils:cron-utils:9.2.1")

    implementation("software.amazon.awssdk:s3:2.34.0")

    implementation("io.hypersistence:hypersistence-utils-hibernate-71:3.14.1")

    implementation("com.bethibande.process:annotations:1.5")
    annotationProcessor("com.bethibande.process:processor:1.5")

    // Jackson & Hibernate Search ORM
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.21.0")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-hibernate-search-orm-elasticsearch")

    // Security
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-security-jpa")
    implementation("io.quarkus:quarkus-security")

    // Web
    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.6.2")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
}

openApiGenerate {
    generatorName.set("typescript-fetch")
    inputSpec.set("${layout.buildDirectory.get()}/openapi/openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/openapi")
}

tasks.withType<GenerateTask> {
    finalizedBy("copyOpenAPITypes")
}

tasks.register<Sync>("copyOpenAPITypes") {
    from("${layout.buildDirectory.get()}/generated/openapi")
    into("${layout.projectDirectory}/src/main/webui/src/generated")

    doLast {
        kotlin.io.path.Path("${layout.projectDirectory}/src/main/webui/src/generated")
            .walk()
            .forEach { path -> path.writeText("// @ts-nocheck\n" + path.readText()) }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}