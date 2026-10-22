tasks.register<Exec>("dockerUp") {
    group = "docker"
    description = "Starts the Docker infrastructure (PostgreSQL) and waits until it is healthy."
    commandLine("docker-compose", "-f", "docker/compose.yaml", "up", "-d", "--wait")
}

tasks.register<Exec>("dockerDown") {
    group = "docker"
    description = "Stops the Docker infrastructure (data volume is kept)."
    commandLine("docker-compose", "-f", "docker/compose.yaml", "down")
}
