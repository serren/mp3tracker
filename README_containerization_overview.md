# Table of contents

- [What to do](#what-to-do)
- [Sub-task 1: Dockerfiles](#sub-task-1-dockerfiles)
- [Sub-task 2: Docker Compose file](#sub-task-2-docker-compose-file)
- [Notes](#notes)
- [Checklist: Before you submit the task](#checklist-before-you-submit-the-task)

## What to do

In this module, you will adapt your services to use a containerization approach.

## Sub-task 1: Dockerfiles

1. **Create a Dockerfile for each service**. Make sure to follow these requirements:

   - Implement **two-stage builds** to create a clear separation between build and runtime environments, which helps keep the final image size small.
   - Use **Alpine images** to keep the resulting images lightweight (below are the recommended ones):
       - **Build stage**:
           - For Maven projects, use [Eclipse Temurin-based Alpine images](https://hub.docker.com/_/maven/tags?name=eclipse-temurin-17-alpine). These allow you to build Java applications efficiently while keeping the environment minimal.
           - For Gradle projects, use [Gradle Alpine images](https://hub.docker.com/_/gradle/tags?name=jdk17-alpine), designed specifically for building Java applications with Gradle.
       - **Runtime stage**:
           - Use [Eclipse Temurin Alpine images](https://hub.docker.com/_/eclipse-temurin/tags?name=17-jre-alpine) for running the application. These images include only the necessary JRE components, minimizing resource usage.
   - Introduce **dependency caching** to speed up rebuilds. This leverages Docker's layer caching to avoid re-downloading unchanged dependencies.
        - **Tips for Maven projects**:
            - Copy the `pom.xml` file before copying the source code (`src`). This allows Docker to cache dependencies if the configuration file has not changed.
            - Avoid `COPY . .` in build stage. Instead, copy files selectively to ensure Docker builds only when necessary, like `COPY src ./src`.
            - Use the command `RUN mvn dependency:go-offline` to download all dependencies before copying the source code.
        - **Tips for Gradle projects**:
            - Copy the Gradle wrapper and build configuration files (`build.gradle`, `settings.gradle`, `gradlew`) first, then install dependencies (e.g., `RUN ./gradlew dependencies --no-daemon`) This helps cache dependencies effectively.
   - **Additional tips**:
        - **General**:
            - Use `WORKDIR` to specify a consistent context for commands (e.g., `WORKDIR /app`). By using `WORKDIR`, you ensure all subsequent commands operate within a defined context without additional setup. Also, `WORKDIR` automatically creates the directory if it doesn’t already exist, so there’s no need for a separate `RUN mkdir /app` command.
            - Prefer `COPY` over `ADD` for local files, as `ADD` can introduce unexpected behavior by unpacking files or fetching URLs.
            - Avoid hardcoded JAR names by using wildcards. For example, instead of `COPY --from=build /app/target/my-application-1.0.0.jar app.jar` use `COPY --from=build /app/target/*.jar app.jar`. This way, you don’t need to update the Dockerfile if the JAR file name changes, as long as there’s only one JAR file in the target directory.
            - Use `CMD` instead of `ENTRYPOINT` to allow flexibility in overriding commands in Docker Compose or when running the container manually.
            - Use `EXPOSE` to indicate the application’s internal port in the runtime stage, e.g., `EXPOSE 8080`.
            - Avoid defining environment variables in the Dockerfile for runtime-specific values with `ARG` or `ENV`.
        - **Tips for Maven projects**:
            - Use `RUN mvn clean package -Dmaven.test.skip=true` in the Dockerfile build stage to skip both test compilation and execution for faster builds. If you want to skip running the tests but still need the test classes available, use `RUN mvn clean package -DskipTests`.
        - **Tips for Gradle projects**:
            - Include the Gradle Wrapper (`gradlew`) in your project and run all commands via `gradlew` to avoid host dependency issues. Update the `.gitignore` file to ensure that `gradlew` and `gradlew.bat` files are included in the Git repository for Docker compatibility.
            - Use the `--no-daemon` flag with `gradlew` to ensure consistent builds within Docker and manage memory usage effectively.
            - Use `RUN ./gradlew assemble --no-daemon -x test` to skip tests and speed up the Docker build. The `assemble` task compiles and packages the code without running tests by default. Adding `-x test` further ensures tests are excluded, maximizing build efficiency. This approach is faster than using `gradle build`, which includes tests by default.

2. **Test the Docker images**

    - Build Docker images for each service.
    - Run the Docker containers and **map external ports** to verify that the application starts correctly and responds to HTTP requests (e.g., using Postman).


## Sub-task 2: Docker Compose file

### 1. Container configuration

Create a `compose.yaml` (`docker-compose.yaml`) file that includes the following elements:

- **Database containers**. Make sure to follow these requirements:

  - **For each database, create a separate container** using lightweight [Alpine-based PostgreSQL images](https://hub.docker.com/_/postgres/tags?name=17-alpine) (version 16 or higher).
  - **Database-specific configurations**, such as `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, should be read from the `.env` file.
  - **Schema management approach:**
      - In **Module 1**, schema initialization was fully automated using **Hibernate** (`ddl-auto=update`), while SQL initialization scripts (`schema.sql`, `data.sql`) were explicitly prohibited.
      - In **Module 2**, the goal is to transition to a **containerized database setup**, where schema initialization is still fully automated but must be handled via predefined SQL scripts executed within the database container, not by Spring or an ORM.
      - Using migration tools (Liquibase, Flyway, etc.) is not allowed.
      - Ensure that `spring.jpa.hibernate.ddl-auto` is set to `none` or removed in `application.properties` or `application.yml`, and that it is not passed as an environment variable in Docker Compose.
      - The following settings must not be present in `application.properties` or `application.yml` and must not be set as environment variables in Docker Compose:
          - `sql.init.mode`
          - `spring.jpa.defer-datasource-initialization`
          - `spring.flyway.enabled`
          - `spring.liquibase.enabled`, etc.
  - **Handle schema initialization externally using the database container**:
      - Schema initialization must be fully automated but executed **within the database container**.
      - SQL scripts should be placed in a dedicated directory (e.g., `init-scripts/`) and **mounted into the database container** using Docker volumes.
      - The scripts must define tables, but must NOT create the database itself.
      - The `POSTGRES_DB` environment variable in `compose.yaml` must be used to create the database automatically.

- **Microservice containers**. Ensure you comply with these requirements:

    - For each service, add a block with the `build` parameter to build images directly from the source code using the Dockerfile located in each service’s subdirectory.
    - To avoid confusion, do not use both `build` and `image` together. The `image` property is intended to pull pre-built images from a registry (e.g., Docker Hub) or assume images are manually built.
    - Specify ports (`ports`) to expose for external access.
    - Define environment variables (`environment`), including database references, using an `.env` file for variable substitution.


<img src="images/containerization_overview.png" width="351" style="border: 1px solid #ccc; padding: 10px; margin: 10px 0; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); display: inline-block;" alt=""/>


### 2. Microservice configuration

Be sure to meet all these conditions:

   - Avoid hardcoding container-specific values (such as database URLs, credentials, service URLs, and any other configuration details specific to the containerized environment) directly in `application.properties` or `application.yml`.
   - Set container-specific values as environment variables in Docker Compose.
   - Use a `.env` file to define these variables, allowing Docker Compose to automatically read and inject environment-specific settings. For example:

       ```properties
       # .env
       RESOURCE_DB_URL=jdbc:postgresql://resource-db:5432/resource_db
       ```

       ```yaml
       # compose.yaml
       services:
         resource-service:
           environment:
             SPRING_DATASOURCE_URL: ${RESOURCE_DB_URL}
       ```

   - Configure `application.properties` or `application.yml` to support both environment variables for containerized execution and default values for local execution (e.g., when running directly in IntelliJ). This approach ensures smooth transitions between development and deployment environments. For example::

     ```properties
     # application.properties for Resource Service
     spring.datasource.url="${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/resource_db}"
     ```

   - Ensure the application can be executed both locally and in Docker Compose without requiring configuration changes or switching profiles:
       - **Local execution**:
         - Only the **database containers** should run in Docker: use a `docker compose up` command specifying the database services, e.g.: `docker compose up -d resource-db song-db`)
         - **Microservices** should run directly on the local machine (as in Module 1).
         - The application should use default values specified in `application.properties` or `application.yml` to connect to the database containers.
       - **Docker execution**:
         - Both **microservices** and **database containers** run fully in Docker Compose.
         - Docker Compose should pull configuration values from the `.env` file automatically, allowing the containerized environment to use the necessary settings without manual adjustments.


### 3. Additional notes 

Adhere to the specified requirements:

   - Use Docker Compose's **default network**.
   - Use **logical service names** to cross-reference services for easier communication within the Docker network instead of IP addresses.
   - **Persisting database data** between restarts is not enabled.


## Notes

- Your configuration should automatically rebuild images, create, and start all containers, ensuring a complete service update without extra steps or scripts — all with a single command: `docker compose up -d --build`.
- Use the [Postman collection](../microservice_architecture_overview/api-tests/introduction_to_microservices.postman_collection.json) for testing the Resource Service and Song Service APIs.
- After all the changes, your project structure should look similar to this:

```
microservices/
├── init-scripts/
│   ├── resource-db/
│   │   └── init.sql
│   └── song-db/
│       └── init.sql
├── resource-service/
│   ├── src/
│   └── Dockerfile
├── song-service/
│   ├── src/
│   └── Dockerfile
├── compose.yaml
├── .env
└── .gitignore
```

---

## Checklist: Before you submit the task

Before submitting your task, please ensure that you have completed all the required steps:

✅ **Dockerfiles**
- [ ] Created a Dockerfile for each service following best practices.
- [ ] Used two-stage builds to separate the build and runtime environments.
- [ ] Used Alpine-based images to keep the final image size small.
- [ ] Implemented dependency caching for faster builds.
- [ ] Used `WORKDIR` to set a consistent working directory.
- [ ] Used `COPY` instead of `ADD` for local files.
- [ ] Avoided hardcoded JAR names by using wildcards.
- [ ] Used `CMD` instead of `ENTRYPOINT` to allow flexibility in overriding commands.
- [ ] Exposed the correct application port.

✅ **Docker Compose**
- [ ] Created a `compose.yaml` (`docker-compose.yaml`) file in the project root directory.
- [ ] Used Alpine-based PostgreSQL images for the databases.
- [ ] Ensured each service has its own database container.
- [ ] Moved database credentials and configuration to an `.env` file.
- [ ] Schema initialization is fully automated and handled inside the database containers, not by Spring Boot.
- [ ] Initialization scripts (`init.sql`) are not used to create databases, only tables.
- [ ] Used Docker volumes to mount initialization scripts (`init.sql`) for table creation.
- [ ] Disabled automatic tables generation (`spring.jpa.hibernate.ddl-auto` set to `none` or removed, and not defined as an environment variable in Docker Compose).
- [ ] Did not use migration tools such as Flyway or Liquibase.
- [ ] Persisting database data between restarts is not enabled (no mounted database volume).
- [ ] Used `build` instead of `image` in the microservice definitions.
- [ ] Configured services to reference each other by service names instead of IP addresses.
- [ ] Used Docker Compose's default network.

✅ **Microservice configuration**
- [ ] Removed hardcoded values (e.g., database URLs, credentials) from `application.properties`, except default ones for local execution.
- [ ] Used environment variables for all container-specific values.
- [ ] Ensured `application.properties` supports both local and Docker execution.
- [ ] Verified that services run both locally and in Docker Compose without profile switching.

✅ **Testing**
- [ ] Used the provided [Postman collection](../microservice_architecture_overview/api-tests/introduction_to_microservices.postman_collection.json) and [sample MP3 file](../microservice_architecture_overview/sample-mp3-file/mp3.zip) to test APIs both locally and in Docker Compose.
- [ ] Verified that all API requests return [correct responses](../microservice_architecture_overview/api-tests/api-response-specification.md) both locally and in Docker Compose.

✅ **Project structure**
- [ ] Ensured the final project structure follows the required format.

✅ **Final submission**
- [ ] Committed all changes to the Git repository.
- [ ] Ready to place the link to your repository in the personal folder in Avalia.
