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
- An InfluxDB 3 instance. The database URL, token and name must be supplied as environment variables (see below).

## Configuration

Configuration values are defined in `src/main/resources/application.yml`. Override them by exporting environment variables before starting the application. At minimum set:

| Environment variable     | Description                                        |
|--------------------------|----------------------------------------------------|
| `INFLUXDB_URL`           | Base URL of your InfluxDB 3 cluster                |
| `INFLUXDB_TOKEN`         | Database token with read permissions               |
| `INFLUXDB_DATABASE`      | Name of the database to query                      |
| `INFLUXDB_READ_TIMEOUT`  | Query timeout in milliseconds (default 90000)      |
| `INFLUXDB_WRITE_TIMEOUT` | Write timeout in milliseconds (default 90000)      |
| `RETRY_INITIAL_INTERVAL` | Initial delay before retrying (ms)                 |
| `RETRY_MULTIPLIER`       | Multiplier used by the exponential back‑off policy |
| `RETRY_MAX_INTERVAL`     | Maximum back‑off delay (ms)                        |
| `SERVER_PORT`            | HTTP port (default 8080)                           |

Export variables in your shell before running the application, for example:

```bash
export INFLUXDB_URL="https://your-cluster.example" \
       INFLUXDB_TOKEN="your-token" \
       INFLUXDB_DATABASE="mydb" \
       SERVER_PORT=8080
```

## Building and Running

This is a standard Maven project. To start the application directly (without building a standalone JAR first) export the needed environment variables and use the Spring Boot Maven plugin:

```bash
INFLUXDB_URL="https://us-east-1-1.aws.cloud2.influxdata.com" \
INFLUXDB_TOKEN="my-token" \
INFLUXDB_DATABASE="my-db" \
mvn spring-boot:run -Dspring-boot.run.jvmArguments="--add-opens=java.base/java.nio=ALL-UNNAMED"
```

The `--add-opens` argument ensures access to internal NIO classes if required by Arrow or native memory operations.

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
