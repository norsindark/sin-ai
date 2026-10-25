plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:storage-service"))
    implementation(project(":modules:media-processor"))
    implementation(project(":modules:ai-engine"))
    implementation(project(":modules:subtitle-workflow"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.minio)
}
