package com.example.influxretry.service;

import com.influxdb.v3.client.InfluxDBClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that encapsulates retry logic for executing SQL queries against InfluxDB.
 * <p>
 * The {@link RetryTemplate} provided by Spring Retry ensures that transient errors
 * are retried according to the policies defined in {@code InfluxDbConfig}. Each call
 * measures the time taken and logs the result for later analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final InfluxDBClient influxDBClient;
    private final RetryTemplate retryTemplate;

    /**
     * Execute a SQL query against InfluxDB. The query is performed within a
     * {@link RetryTemplate#execute(org.springframework.retry.RetryCallback)} call to
     * automatically handle retries. The result stream is converted into a {@link List}.
     *
     * @param sql SQL query to execute
     * @return list of rows returned by the query
     */
    public List<Object[]> queryWithRetry(String sql) {
        return retryTemplate.execute(context -> {
            long start = System.currentTimeMillis();
            String status = "FAILURE";
            try (Stream<Object[]> stream = influxDBClient.query(sql)) {
                List<Object[]> results = stream.collect(Collectors.toList());
                status = "SUCCESS";
                return results;
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                log.info("Query executed: {} in {} ms with status {}", sql, elapsed, status);
            }
        });
    }
}