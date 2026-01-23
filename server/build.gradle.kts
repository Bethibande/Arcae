plugins {
    id("java")
    id("io.quarkus")
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
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-liquibase")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-hibernate-orm")
    implementation("io.quarkus:quarkus-rest")

    implementation("software.amazon.awssdk:s3:2.34.0")

    implementation("io.quarkus:quarkus-smallrye-openapi")

    implementation("io.hypersistence:hypersistence-utils-hibernate-71:3.14.1")

    implementation("com.bethibande.process:annotations:1.5")
    annotationProcessor("com.bethibande.process:processor:1.5")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.21.0")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-hibernate-search-orm-elasticsearch")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}