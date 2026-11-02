plugins {
    id("java-library-conventions")
}

dependencies {
    api(project(":modules:common"))
    implementation(libs.spring.boot.starter)
    implementation(libs.minio)
    constraints {
        implementation(libs.bcprov) {
            because("minio 8.x pulls bcprov 1.78.1 with CRITICAL CVE-2025-14813 and CVE-2026-8763")
        }
    }
}
