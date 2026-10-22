plugins {
    id("java-library-conventions")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:storage-service"))
    implementation(project(":modules:media-processor"))
    implementation(project(":modules:ai-engine"))
}
