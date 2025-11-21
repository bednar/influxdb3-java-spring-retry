package com.example.influxretry.service;

import java.util.List;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.PointValues;
import com.influxdb.v3.client.internal.GrpcCallOptions;
import com.influxdb.v3.client.query.QueryOptions;

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
    public <R> R queryWithRetry(String sql, QueryService.StreamHandler<R> handler) {
        return retryTemplate.execute(context -> {

            QueryOptions queryOptions = new QueryOptions(null, null);
            queryOptions.setGrpcCallOptions(new GrpcCallOptions.Builder().build());

            try (Stream<PointValues> resultStream = influxDBClient.queryPoints(sql, queryOptions)) {
                R result = handler.apply(resultStream);
                log.info("Query succeeded on attempt #{}", context.getRetryCount() + 1);
                return result;
            }
        });
    }

    /**
     * Functional interface for handling a stream of PointValues and returning a result.
     *
     * @param <R> the type of the result
     */
    public interface StreamHandler<R> {
        R apply(Stream<PointValues> resultStream);
    }
}
