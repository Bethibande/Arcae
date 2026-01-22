plugins {
    id("java")
    `java-library`
    `maven-publish`
}

group = "de.bethibande.finance"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name = project.name
                description = project.description

                url = "https://github.com/Bethibande/repository"

                licenses {
                    license {
                        name = "GPL-3.0"
                        url = "https://raw.githubusercontent.com/Bethibande/repository/refs/heads/master/LICENSE"
                    }
                }

                developers {
                    developer {
                        id = "bethibande"
                        name = "Max Bethmann"
                        email = "bethibande@gmail.com"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/Bethibande/repository.git"
                    developerConnection = "scm:git:ssh://github.com/Bethibande/repository.git"
                    url = "https://github.com/Bethibande/repository"
                }
            }
        }
    }

    repositories {
        maven {
            name = "Maven-Releases"
            url = uri("http://localhost:8080/repositories/maven/releases")
            isAllowInsecureProtocol = true

            credentials {
                username = providers.gradleProperty("mavenUsername").get()
                password = providers.gradleProperty("mavenPassword").get()
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}