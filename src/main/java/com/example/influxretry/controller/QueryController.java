package com.example.influxretry.controller;

import ch.qos.logback.classic.Level;
import com.example.influxretry.Influxdb3JavaSpringRetryApplication;
import com.example.influxretry.influx.InfluxV3QueryExecutionMetricsLogger;
import com.example.influxretry.service.QueryService;
import com.google.j2objc.annotations.Property;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.influxdb.v3.client.PointValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST controller that exposes an endpoint for executing SQL queries.
 * <p>
 * Use {@code /api/query?q=&lt;your-query&gt;} to run a query against the configured
 * InfluxDB database. The returned the number of rows.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class QueryController {

    //static List<PointValues> memoryHog = new ArrayList<>();

    private final QueryService queryService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${server.shutdown}")
    private String shutdownType;

    /**
     * Execute a SQL query against InfluxDB and return the number of rows.
     *
     * @param sql the SQL to execute (provided via the {@code q} request parameter)
     * @return the number of rows returned by the query
     */
    @GetMapping("/api/query")
    public long query(@RequestParam(name = "q") String sql) {
        return queryService.queryWithRetry(sql, resultStream
                        -> resultStream.map(this::buildData).toList())
                .size();
    }

    /**
     * A simpler list of threads than the one returned by the actuator.
     *
     * @return list of thread information
     */
    @GetMapping("/threads/all")
    public String all() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        StringBuilder sb = new StringBuilder();
        for (Thread t : threadSet) {
            sb.append(t.getName())
                .append("[")
                .append(t.getThreadGroup().getName())
                .append("]: ")
                .append(t.getState())
                .append("\n");
        }
        return sb.toString();
    }

    /**
     * Force a thread interrupt and inspect results in SLF4J log.
     *
     * @param name - name of the thread to be interrupted.
     * @return - result of interrupt attempt, or name not found.
     */
    @GetMapping("/threads/interrupt")
    public String interrupt(@RequestParam(name = "t") String name) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (t.getName().equals(name)) {
                t.interrupt();
                return "Interrupted " + name + "\n";
            }
        }
        return "Not found " + name + "\n";
    }

    /**
     * Get standard information about a thread.
     *
     * @param name - target thread.
     * @return - information or not found.
     */
    @GetMapping("/threads/info")
    public String info(@RequestParam(name = "t") String name) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        StringBuilder sb = new StringBuilder();
        for (Thread t : threadSet) {
            if (t.getName().equals(name)) {
                return t + "\n";
            }
        }
        return "Not found " + name + "\n";
    }

    /**
     * Return a ledger of results from queries
     *
     * @return the ledger.
     */
    @GetMapping("/results/ledger")
    public String ledger() {
        return InfluxV3QueryExecutionMetricsLogger.GetLedger().toString();
    }

    /**
     * Return a list of events at a higher level than INFO.
     * @return
     */
    @GetMapping("/results/log")
    public String log() {
        return InfluxV3QueryExecutionMetricsLogger.GetLoggingEvents()
            .stream()
            .filter(e -> e.getLevel().levelInt > Level.INFO.levelInt)
            .map(event -> {
                    return event.getInstant()
                        + "[" + event.getLoggerName()
                        + "]: " + event + "\n";
            }).toList() + "\n";
    }

    /**
     * Shutdown the SpringApplication.
     *
     * <p>N.B. property <code>server.shutdown=immediate</code> can cause memory leaks.</p>
     */
    @GetMapping("/shutdown")
    public void shutdown() {
        SpringApplication.exit(applicationContext);
        log.info("Shutdown type {}", shutdownType);
    }

    /**
     * Dummy method to simulate processing of point values.
     *
     * @param pointValues the point values to process
     * @return the processed point values
     */
    private PointValues buildData(PointValues pointValues) {
        log.debug("building point values: {}", pointValues);
   //     memoryHog.add(pointValues);
        return pointValues;
    }
}
