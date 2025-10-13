package com.flab.testrepojava.interceptor;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MetricsFilter implements Filter {

    private final MeterRegistry registry;

    public MetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResp = (HttpServletResponse) response;

        try {
            chain.doFilter(request, response);
        } finally {
            int status = httpResp.getStatus();

            registry.counter("product_api_requests_total").increment();

            if (status >= 400) {
                registry.counter("product_api_errors", "status", String.valueOf(status)).increment();
            }
        }
    }

}
