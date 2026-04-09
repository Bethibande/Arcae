plugins {
    id("java")
    `java-library`
    `maven-publish`
}

group = "de.bethibande.repo"
version = "1.0-snapshot"

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

                url = "https://github.com/Bethibande/Arcae"

                licenses {
                    license {
                        name = "FSL Apache 2"
                        url = "https://raw.githubusercontent.com/Bethibande/arcae/refs/heads/master/LICENSE"
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
                    connection = "scm:git:git://github.com/Bethibande/arcae.git"
                    developerConnection = "scm:git:ssh://github.com/Bethibande/arcae.git"
                    url = "https://github.com/Bethibande/arcae"
                }
            }
        }
    }

    repositories {
        maven {
            name = "Maven-Snapshots"
            url = uri("http://localhost:8080/repositories/maven/maven")
            isAllowInsecureProtocol = true

            credentials {
                username = "admin"
                password = "pEfYqZqtlKkmK7PXWlqQ5FYP7AD1SVVS0HnsDHgd9dGunIokjADFJL098lYrJ96yvZYN0s66eXjmuSFnRINvTsrVUR8vR1MOD5TIGcOofjg8HApxAe6MZYwhMGWYP5Io"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}