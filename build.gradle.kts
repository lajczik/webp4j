plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp") version "1.6.2"
    id("com.gradleup.nmcp.aggregation") version "1.6.2"
}

group = "io.github.lajczik"
version = "2.7.1"

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
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    nmcpAggregation(project(":"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val compileJava = tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

val compileJava22 = tasks.register<JavaCompile>("compileJava22") {
    dependsOn(compileJava)
    source = fileTree("src/main/java22")
    destinationDirectory.set(layout.buildDirectory.dir("classes/java22"))
    classpath = compileJava.get().outputs.files
    options.release.set(22)
}

tasks.named<Jar>("jar") {
    dependsOn(compileJava22)
    archiveBaseName.set("webp4j")
    manifest {
        attributes(
            "Multi-Release" to "true",
            "Automatic-Module-Name" to "xyz.lychee.webp4j",
            "Implementation-Title" to "webp4j",
            "Implementation-Version" to project.version
        )
    }
    into("META-INF/versions/22") {
        from(compileJava22.map { it.destinationDirectory })
    }
}

tasks.named<Jar>("sourcesJar") {
    from("src/main/java22") {
        into("META-INF/versions/22")
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
    dependsOn(compileJava22)
    classpath = files(compileJava22.map { it.destinationDirectory }) + classpath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useJUnitPlatform()
}

// The same suite against the JNI backend (what Java 8-21 users get): the Java 22 classes stay off the classpath.
val testJni = tasks.register<Test>("testJni") {
    description = "Runs the test suite against the JNI backend"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("webp4j.backend", "jni")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(testJni)
}

////////////////////////////////////
//                                //
//    Publishing And Signing      //
//                                //
////////////////////////////////////

// Generate pom file for maven central

fun MavenPom.populate() {
    packaging = "jar"
    name.set(project.name)
    description.set("Lightweight and high-performance WebP and Animated WebP codec library for the JVM with Java ImageIO support")
    url.set("https://github.com/lajczik/webp4j")
    scm {
        url.set("https://github.com/lajczik/webp4j")
        connection.set("scm:git:git://github.com/lajczik/webp4j.git")
        developerConnection.set("scm:git:ssh://git@github.com:lajczik/webp4j.git")
    }
    licenses {
        license {
            name.set("MIT License")
            url.set("https://opensource.org/licenses/MIT")
            distribution.set("repo")
        }
    }
    developers {
        developer {
            id.set("lychee")
            name.set("lajczik")
            email.set("lajczik@gmail.com")
        }
        developer {
            id.set("MrNanko")
            name.set("MrNanko")
            email.set("i@matrixlab.dev")
        }
    }
}

val mavenCentralUsername: String? = (findProperty("mavenCentralUsername") as String?)
    ?: System.getenv("MAVENCENTRAL_USERNAME")?.takeIf { it.isNotBlank() }
val mavenCentralPassword: String? = (findProperty("mavenCentralPassword") as String?)
    ?: System.getenv("MAVENCENTRAL_TOKEN")?.takeIf { it.isNotBlank() }
val gpgSecretKey: String? = (findProperty("signingKey") as String?)
    ?: System.getenv("GPG_SECRET_KEY")?.takeIf { it.isNotBlank() }
val gpgPassphrase: String? = (findProperty("signingPassword") as String?)
    ?: System.getenv("GPG_PASSPHRASE")?.takeIf { it.isNotBlank() }

val stagingDirectory = layout.buildDirectory.dir("staging-deploy").get()

publishing {
    publications {
        register<MavenPublication>("Release") {
            from(components["java"])

            artifactId = project.name
            groupId = project.group as String
            version = project.version as String

            pom.populate()
        }
    }
}

if (gpgSecretKey != null) {
    signing {
        val formattedKey = if (!gpgSecretKey.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
            val cleanKey = gpgSecretKey.trim().replace("\\r?\\n".toRegex(), "")
            "-----BEGIN PGP PRIVATE KEY BLOCK-----\n\n$cleanKey\n-----END PGP PRIVATE KEY BLOCK-----"
        } else {
            gpgSecretKey
        }
        useInMemoryPgpKeys(formattedKey, gpgPassphrase ?: "")
        sign(publishing.publications)
    }
}

nmcpAggregation {
    localRepository {
        name = "staging-deploy"
        path = stagingDirectory.asFile.path
    }

    centralPortal {
        username.set(mavenCentralUsername)
        password.set(mavenCentralPassword)
    }
}
