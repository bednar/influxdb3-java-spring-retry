package com.example.influxretry.controller;

import com.example.influxretry.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes an endpoint for executing SQL queries.
 * <p>
 * Use {@code /api/query?q=&lt;your-query&gt;} to run a query against the configured
 * InfluxDB database. The returned JSON is a list where each element represents
 * a row returned by the query.
 */
@RestController
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /**
     * Execute a SQL query against InfluxDB.
     *
     * @param sql the SQL to execute (provided via the {@code q} request parameter)
     * @return the query results as a list of object arrays
     */
    @GetMapping("/api/query")
    public List<Object[]> query(@RequestParam(name = "q") String sql) {
        return queryService.queryWithRetry(sql);
    }
}
