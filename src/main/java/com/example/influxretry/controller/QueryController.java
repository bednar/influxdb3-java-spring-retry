package com.example.influxretry.controller;

import com.example.influxretry.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes an endpoint for executing SQL queries.
 * <p>
 * Use {@code /api/query?q=&lt;your-query&gt;} to run a query against the configured
 * InfluxDB database. The returned the number of rows.
 */
@RestController
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /**
     * Execute a SQL query against InfluxDB and return the number of rows.
     *
     * @param sql the SQL to execute (provided via the {@code q} request parameter)
     * @return the number of rows returned by the query
     */
    @GetMapping("/api/query")
    public long query(@RequestParam(name = "q") String sql) {
        return queryService.queryWithRetry(sql).size();
    }
}
