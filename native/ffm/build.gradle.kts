plugins {
    `java-library`
    `maven-publish`
}

group = "xyz.lychee.webp4j"
version = "2.5.0"
description = "WebP4j Foreign Function & Memory (FFM) backend for Java 22+"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":native:common"))

    testImplementation(project(":native:jni"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(22)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("webp4j-native-ffm")
    manifest {
        attributes(
            "Automatic-Module-Name" to "xyz.lychee.webp4j.ffm",
            "Implementation-Title" to "webp4j-native-ffm",
            "Implementation-Version" to project.version
        )
    }
}

tasks.withType<Javadoc>().configureEach {
    options {
        if (this is StandardJavadocDocletOptions) {
            encoding = "UTF-8"
            charSet = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

tasks.named<Test>("test") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "webp4j-native-ffm"
            from(components["java"])
            pom {
                name.set("webp4j-native-ffm")
                description.set("WebP4j Foreign Function & Memory (FFM) backend for Java 22+")
                url.set("https://github.com/MrNanko/webp4j")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("MrNanko")
                        name.set("MrNanko")
                        email.set("i@matrixlab.dev")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/MrNanko/webp4j.git")
                    developerConnection.set("scm:git:ssh://git@github.com:MrNanko/webp4j.git")
                    url.set("https://github.com/MrNanko/webp4j.git")
                }
            }
        }
    }
}
