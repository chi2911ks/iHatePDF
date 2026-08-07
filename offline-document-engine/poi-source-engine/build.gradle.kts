plugins {
    `java-library`
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
    // poi-ooxml-lite is generated from XML schemas during the upstream release build;
    // the generated Java sources are not stored in the POI Git repository.
    api(fileTree("../libs") {
        include("*.jar")
        exclude("poi-5.5.1.jar", "poi-ooxml-5.5.1.jar", "poi-scratchpad-5.5.1.jar")
    })
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
