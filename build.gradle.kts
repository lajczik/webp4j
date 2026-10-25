plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "xyz.lychee.webp4j"
version = "2.5.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":native:common"))
    api(project(":native:jni"))

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    testImplementation(project(":native:ffm"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.matching { it.name.startsWith("test") && it.isCanBeResolved }.configureEach {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 22)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("webp4j")
    manifest {
        attributes(
            "Automatic-Module-Name" to project.group.toString(),
            "Implementation-Title" to "webp4j",
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
            artifactId = "webp4j"
            from(components["java"])
            pom {
                name.set("webp4j")
                description.set("WebP4j is a high-performance Java library, powered by JNI and FFM bindings to Google's libwebp, for encoding and decoding WebP images. It also supports converting GIFs to WebP, building animated WebP files, and choosing between lossless and lossy compression.")
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
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY") ?: System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD") ?: System.getenv("GPG_PASSPHRASE")
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
