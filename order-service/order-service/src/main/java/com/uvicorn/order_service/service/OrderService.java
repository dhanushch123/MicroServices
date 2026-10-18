package com.uvicorn.order_service.service;

import com.uvicorn.order_service.client.InventoryOpenFeignClient;
import com.uvicorn.order_service.dto.OrderRequestDto;
import com.uvicorn.order_service.dto.OrderRequestItemDto;
import com.uvicorn.order_service.entity.OrderItem;
import com.uvicorn.order_service.entity.OrderStatus;
import com.uvicorn.order_service.entity.Orders;
import com.uvicorn.order_service.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;
    private final InventoryOpenFeignClient inventoryOpenFeignClient;

    public List<OrderRequestDto> getAllOrders() {
        log.info("Fetching all orders");
        List<Orders> orders = orderRepository.findAll();
        return orders.stream().map(order -> modelMapper.map(order, OrderRequestDto.class)).toList();
    }

    public OrderRequestDto getOrderById(Long id) {
        log.info("Fetching order with ID: {}", id);
        Orders order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        return modelMapper.map(order, OrderRequestDto.class);
    }


    @Transactional
//    @Retry(name = "inventoryRetry" , fallbackMethod = "fallbackCreateOrder")
//    @RateLimiter(name = "inventoryRateLimiter", fallbackMethod = "fallbackCreateOrder")
    @CircuitBreaker(name = "inventoryCircuitBreaker", fallbackMethod = "fallbackCreateOrder")
    public OrderRequestDto createOrder(OrderRequestDto orderRequestDto) {
        log.info("Creating new order");

        log.info("Calling the createOrder method");
        Double totalPrice = inventoryOpenFeignClient.reduceStock(orderRequestDto);

        Orders orders = new Orders();
        orders.setItems(new ArrayList<>());
        for (OrderRequestItemDto itemDto : orderRequestDto.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(itemDto.getProductId());
            orderItem.setQuantity(itemDto.getQuantity());
            orderItem.setOrder(orders);
            orders.getItems().add(orderItem);
        }
        orders.setTotalPrice(totalPrice);
        orders.setOrderStatus(OrderStatus.CONFIRMED);

        Orders savedOrder = orderRepository.save(orders);

        return modelMapper.map(savedOrder, OrderRequestDto.class);

    }

    public OrderRequestDto fallbackCreateOrder(OrderRequestDto orderRequestDto, Throwable throwable) {
        log.error("Fallback occured due to inventory service failure: {}", throwable.getMessage());
        return new OrderRequestDto(); // Return an empty order or handle as needed
    }
}
