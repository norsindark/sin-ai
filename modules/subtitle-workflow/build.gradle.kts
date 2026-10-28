plugins {
    id("java-library-conventions")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:storage-service"))
    implementation(project(":modules:media-processor"))
    implementation(project(":modules:ai-engine"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.micrometer.observation)
    testImplementation(libs.micrometer.observation.test)
}
