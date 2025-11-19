# InfluxDB3 Java Spring Retry

This project demonstrates how to integrate the [influxdb3‑java](https://github.com/InfluxCommunity/influxdb3-java) client with Spring Boot and Spring Retry.  It exposes a single REST endpoint that accepts a SQL query, executes it against an InfluxDB 3 database and returns the raw rows. 

## Architecture

- **Spring Boot 3.5.7** – the latest stable Spring Boot releaseprovides the application framework and auto‑configuration.
- **influxdb3‑java 1.6.0** – the client library used to talk to InfluxDB 3.  Its Maven coordinates are `com.influxdb:influxdb3-java:1.6.0`.  The `query()` API returns a `Stream<Object[]>` that wraps Apache Arrow buffers.  Streams must be closed or enclosed in a `try-with-resources` block to ensure direct memory is released.
- **Spring Retry** – a `RetryTemplate` configured with an `ExceptionClassifierRetryPolicy` classifies exceptions and only retries on transient network or server errors.  An `ExponentialBackOffPolicy` controls the delay between retry attempts.
- **Lombok** – annotations like `@RequiredArgsConstructor` and `@Slf4j` reduce boiler‑plate.
- **Configuration by annotations** – a `@Configuration` class declares beans for the InfluxDB client and retry template using `@Bean`.  No XML configuration is required.
- **REST Controller** – a simple `@RestController` exposes the `/api/query` endpoint.  A `q` request parameter supplies the SQL to execute and the endpoint returns a number of rows. 
- **Stress test script** – a zsh script under `scripts/` that repeatedly calls the REST endpoint. It prints a timestamp before each request and reports curl/HTTP errors. It also supports configurable concurrency.

## Requirements

- **Java 17 (tested)** or later. This project is built and tested with JDK 17 (for example Eclipse Temurin / OpenJDK).
- Maven (or use the Maven Wrapper provided by your IDE).
- An InfluxDB 3 instance.  The database URL, token and name must be supplied as environment variables or system properties (see below).

## Configuration

Configuration values are defined in `src/main/resources/application.yml` and can be overridden using environment variables or Java system properties.  At minimum you must provide:

| Property                 | Environment variable     | Description                                        |
|--------------------------|--------------------------|----------------------------------------------------|
| `influxdb.url`           | `INFLUXDB_URL`           | Base URL of your InfluxDB 3 cluster                |
| `influxdb.token`         | `INFLUXDB_TOKEN`         | Database token with read permissions               |
| `influxdb.database`      | `INFLUXDB_DATABASE`      | Name of the database to query                      |
| `influxdb.read-timeout`  | `INFLUXDB_READ_TIMEOUT`  | Query timeout in milliseconds (default 90000)      |
| `influxdb.write-timeout` | `INFLUXDB_WRITE_TIMEOUT` | Write timeout in milliseconds (default 90000)      |
| `retry.initialInterval`  | `RETRY_INITIAL_INTERVAL` | Initial delay before retrying (ms)                 |
| `retry.multiplier`       | `RETRY_MULTIPLIER`       | Multiplier used by the exponential back‑off policy |
| `retry.maxInterval`      | `RETRY_MAX_INTERVAL`     | Maximum back‑off delay (ms)                        |
| `server.port`            | `SERVER_PORT`            | HTTP port (default 8080)                           |

You can set these variables in your shell before running the application or pass them as `-D` system properties.

## Building and Running

This is a standard Maven project.  To build the JAR:

```bash
mvn clean package
```

After a successful build the jar will be located in the `target/` directory.  Start the application with your InfluxDB credentials, for example:

```bash
java \
  -Dinfluxdb.url=${INFLUXDB_URL} \
  -Dinfluxdb.token=${INFLUXDB_TOKEN} \
  -Dinfluxdb.database=${INFLUXDB_DATABASE} \
  -jar target/influxdb3-java-spring-retry-0.0.1-SNAPSHOT.jar
```

Alternatively you can define the corresponding environment variables and omit the `-D` options.

## REST Endpoint

Once running, the service exposes a single endpoint:

```
GET /api/query?q=<SQL>
```

Example:

```
curl -G --data-urlencode "q=SELECT time,location,value FROM temperature LIMIT 10" http://localhost:8080/api/query
```

The response will be a number representing the count of rows returned by the query.

## Stress Test Script

A helper script at `scripts/stress_test.sh` can generate repeated load against the endpoint.

Features:
- Prints a timestamp before each call.
- Reports errors clearly:
  - network/curl failures as `CURL_ERROR (exit N)`
  - HTTP errors (status >= 400) as `HTTP_ERROR <code>: <body>`
- Configurable interval via `DELAY` (seconds, default 1).
- Configurable parallelism via the 3rd argument or `CONCURRENCY` environment variable.

Usage:

```bash
# single stream
zsh scripts/stress_test.sh "http://localhost:8080/api/query" "SELECT 1"

# faster rate using env var
DELAY=0.2 zsh scripts/stress_test.sh "http://localhost:8080/api/query" "SELECT 1"

# 10 parallel streams (via arg)
zsh scripts/stress_test.sh "http://localhost:8080/api/query" "SELECT 1" 10

# 5 parallel streams (via env var)
CONCURRENCY=5 zsh scripts/stress_test.sh "http://localhost:8080/api/query" "SELECT 1"
```

Stop with Ctrl-C; the script will terminate all worker processes.

## License

This repository is provided for demonstration purposes without any warranty or guarantee.
