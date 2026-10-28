plugins {
    id("java-library-conventions")
}

dependencies {
    api(project(":modules:common"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.json)
    implementation(libs.micrometer.observation)
    implementation(libs.opencc4j)
    testImplementation(libs.micrometer.observation.test)
}
