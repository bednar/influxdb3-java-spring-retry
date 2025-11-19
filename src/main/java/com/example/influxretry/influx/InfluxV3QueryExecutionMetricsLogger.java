package com.example.influxretry.influx;

import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.example.influxretry.metrics.Metrics;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.PointValues;
import com.influxdb.v3.client.query.QueryOptions;

/**
 * Wrapper around {@link InfluxDBClient} that logs execution metrics for queryPoints(...) calls.
 * <p>
 * Most methods are delegated to the underlying client using Lombok's {@code @Delegate}. The
 * {@code queryPoints(String, QueryOptions)} method is explicitly implemented so we can record
 * elapsed time and whether the call succeeded or failed. This class attempts to emulate the
 * requested production behaviour.
 */
@Slf4j
public class InfluxV3QueryExecutionMetricsLogger implements InfluxDBClient {

    @Delegate
    private final InfluxDBClient influxDBClient;

    public InfluxV3QueryExecutionMetricsLogger(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @Nonnull
    @Override
    public Stream<PointValues> queryPoints(@Nonnull final String query, @Nonnull final QueryOptions queryOptions) {
        long startTime = System.currentTimeMillis();
        String status = Metrics.FAILURE;
        try {
            // The project uses a single-arg queryPoints API; call that to remain compatible.
            Stream<PointValues> stream = influxDBClient.queryPoints(query, queryOptions);
            status = Metrics.SUCCESS;
            return stream;
        } finally {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("Time to execute query: {} is {} ms and status:{}", query, elapsedTime, status);
        }
    }

    public void close() throws Exception {
        log.info("Closing InfluxDBClient");
        influxDBClient.close();
    }
}
