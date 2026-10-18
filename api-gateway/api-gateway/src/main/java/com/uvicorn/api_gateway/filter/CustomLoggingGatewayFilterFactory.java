package com.uvicorn.api_gateway.filter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CustomLoggingGatewayFilterFactory
        extends AbstractGatewayFilterFactory<CustomLoggingGatewayFilterFactory.Config> {

    public CustomLoggingGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {

            log.info("Custom logging filter applied");

            log.info(
                    "Custom logging filter pre processing: {}",
                    config.getMessage()
            );

            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        log.info(
                                "Custom logging filter post processing: {}",
                                exchange.getRequest().getURI()
                        );
                    }));
        };
    }

    @Getter
    @Setter
    public static class Config {
        private String message;
    }
}
