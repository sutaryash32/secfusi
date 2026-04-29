//package com.secufusion.api_gateway.config;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.HttpStatusCode;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.stereotype.Component;
//import org.springframework.cloud.gateway.filter.GatewayFilter;
//import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.server.ServerWebExchange;
//import org.springframework.core.io.buffer.DataBuffer;
//import reactor.core.publisher.Mono;
//import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
//import org.springframework.cloud.gateway.route.Route;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Objects;
//
//@Component("JwtAuthServiceValidationFilter")
//public class JwtAuthServiceValidationFilter extends AbstractGatewayFilterFactory<JwtAuthServiceValidationFilter.Config> {
//
//    private static final Logger logger = LoggerFactory.getLogger(JwtAuthServiceValidationFilter.class);
//
//    private final WebClient webClient;
//    private final String authServiceName;
//    private final String validationPath;
//    private final HttpMethod validationMethod;
//    private final String tokenParamName;
//
//    public JwtAuthServiceValidationFilter(WebClient lbWebClient,
//                               @Value("${auth.service.name:AUTH}") String authServiceName,
//                               @Value("${auth.service.validation-path:/auth/login}") String validationPath,
//                               @Value("${auth.service.validation-method:POST}") String validationMethodName,
//                               @Value("${auth.service.validation-token-param:token}") String tokenParamName) {
//        super(Config.class);
//        this.webClient = lbWebClient;
//        this.authServiceName = Objects.requireNonNull(authServiceName);
//        this.validationPath = validationPath.startsWith("/") ? validationPath : ("/" + validationPath);
//        this.validationMethod = parseHttpMethod(validationMethodName, HttpMethod.POST);
//        this.tokenParamName = tokenParamName;
//    }
//
//    private static HttpMethod parseHttpMethod(String method, HttpMethod defaultMethod) {
//        if (method == null) {
//            return defaultMethod;
//        }
//        try {
//            return HttpMethod.valueOf(method.trim().toUpperCase());
//        } catch (IllegalArgumentException ex) {
//            logger.warn("Invalid HTTP method '{}' for auth service validation, defaulting to {}", method, defaultMethod);
//            return defaultMethod;
//        }
//    }
//
//    @Override
//    public GatewayFilter apply(Config config) {
//        return (exchange, chain) -> {
//            ServerHttpRequest request = exchange.getRequest();
//            logger.debug("Incoming request: {} {}", request.getMethod(), request.getURI());
//            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
//            logger.debug("Authorization header present: {}", authHeader != null);
//
//            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//                logger.info("Rejecting request {} {} due to missing/invalid Authorization header", request.getMethod(), request.getURI());
//                return unauthorized(exchange, "Missing or invalid Authorization header");
//            }
//            String token = authHeader.substring(7);
//
//            String serviceUriPrefix = "lb://" + authServiceName;
//            logger.info("Validating token for request {} {} via auth service {}{} (method={})",
//                    request.getMethod(), request.getURI(), serviceUriPrefix, validationPath, validationMethod);
//
//            Mono<HttpStatusCode> validationResponse;
//
//            // Choose GET or POST based on configuration
//            if (HttpMethod.GET.equals(this.validationMethod)) {
//                // GET -> lb://AUTH{/path}?token=<token>
//                validationResponse = webClient.get()
//                        .uri(uriBuilder ->
//                                uriBuilder.path(serviceUriPrefix + validationPath)
//                                        .queryParam(tokenParamName, token)
//                                        .build())
//                        .accept(MediaType.APPLICATION_JSON)
//                        .exchangeToMono(resp -> Mono.just(resp.statusCode()));
//            } else {
//                // POST -> call lb://AUTH{/path} with Authorization header (some auth services prefer this)
//                validationResponse = webClient.post()
//                        .uri(serviceUriPrefix + validationPath + "?token=" + token)
//                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
//                        .accept(MediaType.APPLICATION_JSON)
//                        .exchangeToMono(resp -> Mono.just(resp.statusCode()));
//            }
//
//            return validationResponse.flatMap(status -> {
//                if (status.is2xxSuccessful()) {
//                    return handleSuccess(exchange, token, chain);
//                } else if (status.value() == HttpStatus.UNAUTHORIZED.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
//                    logger.warn("Token validation failed with status: {}", status);
//                    return unauthorized(exchange, "Unauthorized by auth service");
//                } else {
//                    logger.warn("Auth service returned unexpected status: {}", status);
//                    return unauthorized(exchange, "Auth service error: " + status.toString());
//                }
//            }).onErrorResume(err -> {
//                logger.error("Error calling auth service", err);
//                return unauthorized(exchange, "Auth service unreachable or error");
//            });
//        };
//    }
//
//    private Mono<Void> handleSuccess(ServerWebExchange exchange, String token, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
//        // Forward Authorization header downstream
//        ServerHttpRequest newRequest = exchange.getRequest().mutate()
//                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
//                .build();
//        ServerWebExchange mutatedExchange = exchange.mutate().request(newRequest).build();
//
//        // logging
//        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
//        Object gatewayRequestUrls = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
//        String routeId = route != null ? route.getId() : "unknown-route";
//        logger.info("Forwarding request {} {} to route: {} target: {}", exchange.getRequest().getMethod(),
//                exchange.getRequest().getURI(), routeId,
//                gatewayRequestUrls != null ? gatewayRequestUrls.toString() : "unknown-target");
//
//        return chain.filter(mutatedExchange);
//    }
//
//    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
//        logger.debug("Responding with 401: {}", message);
//        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
//        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
//        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
//        return exchange.getResponse().writeWith(Mono.just(buffer));
//    }
//
//    public static class Config {
//        // no config fields required
//    }
//}
