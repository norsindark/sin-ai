val composeFile = "docker/docker-compose.yml"

val dockerInfraUp = tasks.register<Exec>("dockerInfraUp") {
    group = "docker"
    description = "Starts PostgreSQL and MinIO and waits until they are healthy."
    commandLine("docker", "compose", "-f", composeFile, "up", "-d", "--wait", "postgres", "storage")
}

tasks.register<Exec>("dockerUp") {
    group = "docker"
    description = "Starts the Docker infrastructure and initializes the MinIO buckets and app user."
    dependsOn(dockerInfraUp)
    commandLine("docker", "compose", "-f", composeFile, "run", "--rm", "storage-init")
}

tasks.register<Exec>("dockerDown") {
    group = "docker"
    description = "Stops the Docker infrastructure (data volume is kept)."
    commandLine("docker", "compose", "-f", composeFile, "down")
}
