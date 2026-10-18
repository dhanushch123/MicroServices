package com.uvicorn.order_service.controller;


import com.uvicorn.order_service.client.InventoryOpenFeignClient;
import com.uvicorn.order_service.dto.OrderRequestDto;
import com.uvicorn.order_service.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/core")
@RefreshScope
public class OrderController {

    private final OrderService orderService;

    @Value("${variable.name}")
    private String variable;

    @GetMapping("/helloOrders")
    public String helloOrders(@RequestHeader(name = "X-User-Id",required = false) Long userId) {
        log.info("User Id : {}" , userId);
        log.info("Variable value from application properties: {}", variable);
        return "Hello from Orders Service From GKE : " + variable;
    }

//    @PostMapping("/create-order")
//    public ResponseEntity<OrderRequestDto> createOrder(@RequestBody OrderRequestDto orderRequestDto) {
//        OrderRequestDto orderRequestDto1 = orderService.createOrder(orderRequestDto);
//        return ResponseEntity.ok(orderRequestDto1);
//    }

    @GetMapping
    public ResponseEntity<List<OrderRequestDto>> getAllOrders(HttpServletRequest httpServletRequest) {
        log.info("Fetching all orders via controller");

        List<OrderRequestDto> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);  // Returns 200 OK with the list of orders
    }

    @PostMapping("/create-order")
    public ResponseEntity<OrderRequestDto> createOrder(@RequestBody OrderRequestDto orderRequestDto) {
        log.info("Creating order via controller");
        OrderRequestDto createdOrder = orderService.createOrder(orderRequestDto);
        return ResponseEntity.ok(createdOrder);  // Returns 200 OK with the created order
    }



}
