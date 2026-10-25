plugins {
    id("java-library-conventions")
}

dependencies {
    api(project(":modules:common"))
    implementation(libs.spring.boot.starter)
    implementation(libs.minio)
}
