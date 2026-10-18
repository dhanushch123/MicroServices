package com.uvicorn.inventory_service.controller;



import com.uvicorn.inventory_service.client.OrdersFeignClient;
import com.uvicorn.inventory_service.dto.OrderRequestDto;
import com.uvicorn.inventory_service.dto.ProductDto;
import com.uvicorn.inventory_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final OrdersFeignClient ordersFeignClient;

    @GetMapping("/fetchOrders")
    public String fetchFromOrderService() {
        log.info("Fetching data from Order Service");
        return ordersFeignClient.helloOrders();

    }

    @GetMapping
    public List<ProductDto> getAllProducts() {
        log.info("Fetching all products");
        return productService.getAllInventory();
    }

    @GetMapping("/{id}")
    public ProductDto getProductById(@PathVariable Long id) {
        log.info("Fetching product with id: {}", id);
        return productService.getProductById(id);
    }

    @PutMapping("reduce-stock")
    public ResponseEntity<Double> reduceStocks(@RequestBody OrderRequestDto orderRequestDto) {
        log.info("Reducing stocks for order: {}", orderRequestDto);
        Double totalPrice = productService.reduceStocks(orderRequestDto);
        return ResponseEntity.ok(totalPrice);  // Returns 200 OK with the total price
    }
}
