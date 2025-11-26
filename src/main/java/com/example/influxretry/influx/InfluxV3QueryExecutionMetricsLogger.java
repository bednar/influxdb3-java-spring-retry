package com.example.influxretry.influx;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.influxretry.metrics.Metrics;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.PointValues;
import com.influxdb.v3.client.query.QueryOptions;
import org.slf4j.LoggerFactory;

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

    static List<String> ResultsLedger = new ArrayList<>();
    private static final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    private static final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    static {
        rootLogger.addAppender(listAppender);
        listAppender.start();
    }

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

            ResultsLedger.add(Instant.now() + ": " + status + "\n");
        }
    }

    public void close() throws Exception {
        log.info("Closing InfluxDBClient");
        influxDBClient.close();
    }

    public static List<String> GetLedger(){
        return ResultsLedger;
    }

    public static List<ILoggingEvent> GetLoggingEvents(){
        return listAppender.list;
    }
}
