package com.example.influxretry.service;

import java.util.List;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.PointValues;

/**
 * Service that encapsulates retry logic for executing SQL queries against InfluxDB.
 * <p>
 * The {@link RetryTemplate} provided by Spring Retry ensures that transient errors
 * are retried according to the policies defined in {@code AppConfig}. Each call
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
    public List<PointValues> queryWithRetry(String sql) {
        return retryTemplate.execute(context -> {
            long start = System.currentTimeMillis();
            try (Stream<PointValues> stream = influxDBClient.queryPoints(sql)) {
                List<PointValues> list = stream.map(this::buildData).toList();
                long elapsed = System.currentTimeMillis() - start;
                log.info("Query succeeded in {} ms on attempt #{}: {} rows returned",
                        elapsed, context.getRetryCount() + 1, list.size());
                return list;
            }
        });
    }

    /**
     * Dummy method to simulate processing of point values.
     *
     * @param pointValues the point values to process
     * @return the processed point values
     */
    private PointValues buildData(PointValues pointValues) {
        log.debug("building point values: {}", pointValues);
        return pointValues;
    }
}
