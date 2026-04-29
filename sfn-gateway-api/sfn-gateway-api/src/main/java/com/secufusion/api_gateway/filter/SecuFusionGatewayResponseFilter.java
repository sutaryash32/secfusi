package com.secufusion.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class SecuFusionGatewayResponseFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {

            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            String origin = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.ORIGIN);

            log.info("SECUFUSION-GATEWAY-RESPONSE ::: START");
            log.info("Request Origin → {}", origin);
            log.info("Response Status → {}", response.getStatusCode());

            // ✅ If already handled by CorsWebFilter → do nothing
            if (headers.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)) {
                log.debug("CORS already applied by CorsWebFilter");
                return;
            }

            if (origin == null || origin.isBlank()) {
                return;
            }

            // =========================
            // Allow localhost (UI dev)
            // =========================
            if (origin.startsWith("http://localhost")
                    || origin.startsWith("https://localhost")) {
                applyCors(headers, origin);
                log.info("");
                return;
            }

            // =========================
            // Allow SecuFusion domains
            // =========================
            if (origin.endsWith(".secufusion.net")
                    || origin.endsWith(".motivitylabs.net")) {
                applyCors(headers, origin);
            }

            log.debug("Response Headers → {}", headers);
            log.info("SECUFUSION-GATEWAY-RESPONSE ::: END");
        }));
    }

    private void applyCors(HttpHeaders headers, String origin) {
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
        headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
    }
}
