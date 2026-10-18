package com.uvicorn.api_gateway.filter;

import com.uvicorn.api_gateway.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.Data;
import lombok.Getter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config> {

    private final JwtService jwtService;

    public AuthenticationGatewayFilterFactory(JwtService jwtService) {
        super(Config.class);
        this.jwtService = jwtService;
    }

    @Override
    public GatewayFilter apply(Config config) {


        return (exchange,chain) -> {
            if(!config.isEnabled()) return chain.filter(exchange);
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if(authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.split("Bearer ")[1];
            // Extract Claims from token
            try {
                Claims claims = jwtService.getClaimsFromToken(token);
                Long userId = jwtService.getUserId(claims);
                List<String> roles = jwtService.getUserRoles(claims);
                exchange.getRequest().mutate()
                        .header("X-User-Id", userId.toString())
//                        .header("X-User-Roles", String.join(",", roles))
                        .build();
            }
            catch(JwtException e) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }


    @Data
    public static class Config {
        // Put the configuration properties for your filter here
        private boolean isEnabled;
    }
}
