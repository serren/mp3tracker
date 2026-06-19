package com.example.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .doFinally(signal -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    if (status != null && (status.value() == 401 || status.value() == 403)) {
                        log.warn("{} {} {} → {}",
                                exchange.getRequest().getMethod(),
                                exchange.getRequest().getPath(),
                                exchange.getRequest().getRemoteAddress(),
                                status.value());
                    }
                });
    }
}
