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
}
