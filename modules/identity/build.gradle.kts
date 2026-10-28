plugins {
    id("java-library-conventions")
}

dependencies {
    api(project(":modules:common"))
    api(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
}
