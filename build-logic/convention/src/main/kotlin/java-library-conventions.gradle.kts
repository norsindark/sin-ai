import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    id("io.spring.dependency-management")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(catalog.findVersion("java").get().requiredVersion)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

ext["lombok.version"] = catalog.findVersion("lombok").get().requiredVersion

dependencies {
    val lombok = catalog.findLibrary("lombok").get()
    compileOnly(lombok)
    annotationProcessor(lombok)
    testCompileOnly(lombok)
    testAnnotationProcessor(lombok)
    testImplementation(catalog.findLibrary("spring-boot-starter-test").get())
    testRuntimeOnly(catalog.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test> {
    useJUnitPlatform()
}
