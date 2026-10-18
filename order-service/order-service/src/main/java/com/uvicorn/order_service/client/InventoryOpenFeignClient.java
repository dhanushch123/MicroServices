package com.uvicorn.order_service.client;

import com.uvicorn.order_service.dto.OrderRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(
        name = "inventory-service",
        url = "${services.inventory-service.url}",
        path = "/inventory"
)
public interface InventoryOpenFeignClient {

    @PutMapping("/products/reduce-stock")
    Double reduceStock(OrderRequestDto orderRequestDto);
}
