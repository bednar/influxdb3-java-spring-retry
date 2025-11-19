package com.example.influxretry.config;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.classify.Classifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.influxdb.v3.client.InfluxDBApiException;
import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.config.ClientConfig;

/**
 * Configuration class that defines beans for the InfluxDB client and retry behaviour.
 * <p>
 * The {@link RetryTemplate} is configured with an {@link ExceptionClassifierRetryPolicy}
 * so that only transient network and server errors trigger a retry. See the README
 * for details on which exceptions are retried.
 */
@Configuration
@EnableRetry
@Slf4j
public class AppConfig {

    @Value("${influxdb.url}")
    private String host;

    @Value("${influxdb.token}")
    private String token;

    @Value("${influxdb.database}")
    private String database;

    @Value("${influxdb.read-timeout:90000}")
    private long readTimeout;

    @Value("${influxdb.write-timeout:90000}")
    private long writeTimeout;

    @Value("${retry.initialInterval:1000}")
    private long retryDelay;

    @Value("${retry.multiplier:2.0}")
    private double delayMultiplier;

    @Value("${retry.maxInterval:30000}")
    private long maxInterval;

    /**
     * Creates a singleton {@link InfluxDBClient}. The client is closed automatically
     * when the Spring context shuts down thanks to the {@code destroyMethod} attribute.
     *
     * @return configured InfluxDB client
     */
    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient() {
        ClientConfig clientConfig = new ClientConfig.Builder()
                .host(host)
                .token(token != null ? token.toCharArray() : new char[0])
                .database(database)
                .queryTimeout(Duration.of(readTimeout, ChronoUnit.MILLIS))
                .writeTimeout(Duration.of(writeTimeout, ChronoUnit.MILLIS))
                .build();
        InfluxDBClient client = InfluxDBClient.getInstance(clientConfig);

        // wrap client with metrics-logging decorator
        return new com.example.influxretry.influx.InfluxV3QueryExecutionMetricsLogger(client);
    }

    /**
     * Configure a {@link RetryTemplate} with an exponential back‑off and a classifier
     * that decides whether to retry based on the exception chain.
     *
     * @return configured RetryTemplate
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        ExceptionClassifierRetryPolicy policy = new ExceptionClassifierRetryPolicy();
        policy.setExceptionClassifier(configureRetryPolicy());
        retryTemplate.setRetryPolicy(policy);
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryDelay);
        backOffPolicy.setMultiplier(delayMultiplier);
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    /**
     * Defines the classification logic for determining whether an exception should trigger a retry.
     *
     * @return a classifier mapping exceptions to retry policies
     */
    private Classifier<Throwable, RetryPolicy> configureRetryPolicy() {
        RetryPolicy influxQueryRetryPolicy = new SimpleRetryPolicy();
        RetryPolicy neverRetryPolicy = new NeverRetryPolicy();
        return throwable -> {
            Throwable cause = throwable;
            while (cause != null) {
                // Retry on transient Flight errors
                if (cause instanceof FlightRuntimeException fre) {
                    if (fre.status().code() == CallStatus.UNAVAILABLE.code()
                            || fre.status().code() == CallStatus.TIMED_OUT.code()) {
                        return influxQueryRetryPolicy;
                    }
                }
                // Retry on socket timeouts or general socket errors
                if (cause instanceof SocketTimeoutException || cause instanceof SocketException) {
                    return influxQueryRetryPolicy;
                }
                // Retry on specific InfluxDB API exceptions
                if (cause instanceof InfluxDBApiException ide) {
                    String message = ide.getMessage();
                    if (message != null && message.contains("EOF reached while reading")) {
                        return influxQueryRetryPolicy;
                    }
                }
                // Retry on HTTP 504 gateway timeout indicated in the message
                if (cause.getMessage() != null && cause.getMessage().contains("504")) {
                    return influxQueryRetryPolicy;
                }
                cause = cause.getCause();
            }
            // Default: do not retry
            return neverRetryPolicy;
        };
    }
}
