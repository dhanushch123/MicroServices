package com.uvicorn.inventory_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "order-service",
        url = "${services.order-service.url}",
        path = "/orders"
)
public interface OrdersFeignClient {

    @GetMapping("core/helloOrders")
    String helloOrders();
}
