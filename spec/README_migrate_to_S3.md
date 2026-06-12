# Module 1: Microservice architecture overview

## Table of contents

- [What to do](#what-to-do)
- [Sub-task 1: Resource Service](#sub-task-1-resource-service)
- [Sub-task 2: Resource Processor](#sub-task-2-resource-processor)

## What to do

The main objectives are:

1. Make structural changes to the existing **Resource Service**.
2. Develop a new microservice called **Resource Processor**.

## Sub-task 1: Resource Service

For the **Resource Service**, you need to implement the following modifications:

1. **Use Cloud Storage**: Replace the current database storage for resource files with a cloud storage solution, such as an emulator (e.g., [S3 emulator](https://github.com/localstack/localstack)). The resource files were previously saved in the service database.

2. **Resource Tracking**: Update the underlying database to track the resource by storing its location in the cloud storage.

3. **Upload Process**: When a user uploads an MP3 file, the **Resource Service** should:
    - Store the original MP3 file in the cloud storage (or its emulation).
    - Save the file's location (i.e., the link in cloud storage) in the database.
    - Note: In the current module, the **Resource Service** should not call any other services during this process.

4. **Delete Process:** The logic serves as a cleanup mechanism must be **adapted** for the new storage architecture. When a user deletes an MP3 file, the **Resource Service** should:
   - Remove the file from the **Cloud Storage** (S3 bucket).
   - Delete the record from the **Resource DB**.
   - Call **Song Service** to cascade-delete the related song metadata.

## Sub-task 2: Resource Processor

The **Resource Processor** microservice will be responsible for processing MP3 files in the upcoming modules. It will not have a web interface and, in the current module, should be implemented as a basic Spring Boot application with minimal configuration.

- **Initial Functionality**: The service should be able to extract metadata from an MP3 file for future use with the **Song Service** API. You can use an external library like [Apache Tika](https://www.tutorialspoint.com/tika/tika_extracting_mp3_files.htm) to handle the metadata extraction.

- **Basic Spring Boot Setup**: Implement the initial version of the service as a standard Spring Boot project.

The diagram below illustrates the overall microservice architecture:

<img src="images/microservice_architecture_overview_2.png" width="501" style="border: 1px solid #ccc; padding: 10px; margin: 10px 0; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); display: inline-block;" alt=""/>
