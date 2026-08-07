plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sourceSets {
    main {
        java.srcDirs(
            layout.buildDirectory.dir("generated/poi-source"),
            "src/main/java",
        )
        resources.srcDirs(
            "../upstream/apache-poi/poi/src/main/resources",
            "../upstream/apache-poi/poi-ooxml/src/main/resources",
            "../upstream/apache-poi/poi-scratchpad/src/main/resources",
        )
    }
}

val syncPoiSources by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("generated/poi-source"))
    from("../upstream/apache-poi/poi/src/main/java")
    from("../upstream/apache-poi/poi-ooxml/src/main/java") {
        // These optional features depend on XML-security, Batik/FOP and Java2D
        // stacks which are not Android-compatible and are not used by the
        // PDF/Word conversion API.
        exclude(
            "org/apache/poi/poifs/crypt/dsig/**",
            "org/apache/poi/xwpf/usermodel/XWPFSignatureLine.java",
            "org/apache/poi/xssf/usermodel/XSSFSignatureLine.java",
            "org/apache/poi/xslf/usermodel/XSLFSignatureLine.java",
            "org/apache/poi/xslf/draw/SVG*.java",
            "org/apache/poi/xslf/util/PDF*.java",
            "org/apache/poi/xslf/util/SVG*.java",
        )
    }
    from("../upstream/apache-poi/poi-scratchpad/src/main/java")
}

dependencies {
    if (System.getenv("JITPACK") == "true") {
        // Public coordinates are emitted into the JitPack POM/module metadata.
        api("org.apache.poi:poi-ooxml-lite:5.5.1")
        api("org.apache.xmlbeans:xmlbeans:5.3.0")
        api("org.apache.commons:commons-collections4:4.5.0")
        api("org.apache.commons:commons-compress:1.28.0")
        api("commons-io:commons-io:2.21.0")
        api("commons-codec:commons-codec:1.20.0")
        api("org.apache.commons:commons-lang3:3.18.0")
        api("org.apache.commons:commons-math3:3.6.1")
        api("org.apache.logging.log4j:log4j-api:2.24.3")
        api("com.zaxxer:SparseBitSet:1.3")
        api("com.github.virtuald:curvesapi:1.08")
        api("org.bouncycastle:bcprov-jdk15to18:1.72")
        api("org.bouncycastle:bcpkix-jdk15to18:1.72")
        api("org.bouncycastle:bcutil-jdk15to18:1.72")
    } else {
        // Offline repository builds use the pinned local copies.
        api(fileTree("../libs") {
            include("*.jar")
            exclude("poi-5.5.1.jar", "poi-ooxml-5.5.1.jar", "poi-scratchpad-5.5.1.jar")
        })
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(syncPoiSources)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-deprecation")
}

tasks.processResources {
    // Upstream subprojects provide overlapping ServiceLoader descriptors.
    // The converter does not use POI's generic extractor discovery.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    withSourcesJar()
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(syncPoiSources)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "poi-source-engine"
        }
    }
}
