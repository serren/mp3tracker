# Module 7: Monitoring

## Table of contents

- [What to do](#what-to-do)
- [Sub-task 1: Adding logging](#sub-task-1-adding-logging)
- [Sub-task 2: Monitoring](#sub-task-2-monitoring)
- [Sub-task 3: Tracing](#sub-task-3-tracing)
- [Example](#example)

## What to do

This task focuses on enhancing the microservices infrastructure by adding a **Logging system**, setting up **Monitoring**, and implementing **Tracing**.


## Sub-task 1: Adding logging

1. **Update your infrastructure with a data storage solution**:
    - Choose and integrate a logging storage system. Possible options include:
        - [InfluxDB](https://hub.docker.com/_/influxdb)
        - [Prometheus](https://hub.docker.com/r/prom/prometheus)
        - [Elasticsearch](https://hub.docker.com/_/elasticsearch)
    - Ensure the chosen system is added as a container in your `docker-compose.yml` file.

2. **Configure your applications to send logs to the new storage**:
    - Update the logging configuration in your services to send logs to the newly added storage system.
    - The logging can be configured in various ways depending on the chosen system.

3. **Ensure logs are gathered and persisted**:
    - Once configured, logs should be collected from all services and sent to the logging storage system.
    - Use appropriate tools or collectors to gather logs from each service and persist them in the storage system.
    - Ensure that logs are indexed and can be queried easily in the storage system for future analysis.


## Sub-task 2: Monitoring

1. **Expose a route for a visualization tool**:
    - Set up a monitoring tool to visualize metrics from your services. Possible options include:
        - [Grafana](https://hub.docker.com/r/grafana/grafana)
        - [Kibana](https://hub.docker.com/_/kibana)
    - Add the visualization tool as a service in your `docker-compose.yml` file.
    - Expose a route in your **API Gateway service** that makes the necessary metrics available to the visualization tool. This typically involves exposing an endpoint like `/actuator/prometheus` for Prometheus or similar for other tools.
    - Configure the visualization tool to collect and display metrics from the services.

2. **Create dashboards for monitoring**:
    - Develop dashboards based on key metrics, such as:
        - **JVM metrics** (memory usage, garbage collection, thread count, etc.).
        - **API Gateway performance** (request count, latency, error rates).
    - Use the chosen monitoring tool (Grafana, Kibana) to display these metrics in a meaningful way.
    - Configure the tool to refresh the data and ensure that all relevant performance indicators are visible for efficient monitoring.


## Sub-task 3: Tracing

1. **Add and propagate trace ID**:
   - Add a **trace ID** header to the request when a file is uploaded.
   - Ensure that the trace ID is propagated through all downstream HTTP calls made by the service. This can be done by using thread-local storage or HTTP interceptors in your application.
   - Additionally, propagate the trace ID through any message queues by adding it as an attribute to the message when sending it and extracting it when receiving the message.

2. **Extract trace ID and correlate logs**:
   - Once the trace ID is added and propagated across services, extract the trace ID from the request or message in each service.
   - Use the trace ID to find and correlate logs in the chosen visualization tool (e.g., Grafana, Kibana, etc.).
   - Ensure that logs from all involved services can be found using only the trace ID, enabling full visibility into the path of a request across your microservices architecture.


## Example

To set up **monitoring with Prometheus and Grafana** and **log aggregation using the ELK stack**, you can refer to the following resources for step-by-step guides:

- Monitoring with Prometheus and Grafana and log aggregation using ELK stack: [Part 1](https://medium.com/nerd-for-tech/creating-spring-boot-microservices-monitoring-with-prometheus-and-grafana-and-log-aggregation-ba4f20496942), [Part 2](https://medium.com/nerd-for-tech/building-spring-boot-microservices-monitoring-with-prometheus-and-grafana-and-log-aggregation-5ed9ca7dda36)

> **Note:** Ensure that all additional components (InfluxDB, Prometheus, Elasticsearch, Logstash, Grafana, Kibana, etc.) are launched as containers using `docker-compose` files for easy management and orchestration.