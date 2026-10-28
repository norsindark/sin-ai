plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:storage-service"))
    implementation(project(":modules:media-processor"))
    implementation(project(":modules:ai-engine"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:subtitle-workflow"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.opentelemetry.logback.appender)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.minio)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
}
