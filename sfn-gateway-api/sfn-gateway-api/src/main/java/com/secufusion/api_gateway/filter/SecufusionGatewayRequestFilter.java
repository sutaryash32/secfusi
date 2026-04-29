package com.secufusion.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class SecufusionGatewayRequestFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        log.info("SECUFUSION-GATEWAY-REQUEST ::: START");

        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);

        log.info("Incoming Request → {} {}", method, path);
        log.info("Origin → {}", origin);

        HttpHeaders headers = exchange.getRequest().getHeaders();
        headers.forEach((key, value) ->
                log.debug("Header :: {} = {}", key, value)
        );

        return chain.filter(exchange);
    }
}
