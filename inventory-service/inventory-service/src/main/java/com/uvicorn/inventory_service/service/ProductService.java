package com.uvicorn.inventory_service.service;

import com.uvicorn.inventory_service.dto.OrderRequestDto;
import com.uvicorn.inventory_service.dto.OrderRequestItemDto;
import com.uvicorn.inventory_service.dto.ProductDto;
import com.uvicorn.inventory_service.entity.Product;
import com.uvicorn.inventory_service.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;

    public List<ProductDto> getAllInventory() {
        log.info("Fetching all inventory items");
        List<Product> inventories = productRepository.findAll();
        return inventories.stream()
                .map(product -> modelMapper.map(product, ProductDto.class))
                .toList();
    }

    public ProductDto getProductById(Long id) {
        log.info("Fetching Product with ID: {}", id);
        Optional<Product> inventory = productRepository.findById(id);
        return inventory.map(item -> modelMapper.map(item, ProductDto.class))
                .orElseThrow(() -> new RuntimeException("Inventory not found"));
    }


    @Transactional
    public Double reduceStocks(OrderRequestDto orderRequestDto) {
        // Implementation for reducing stocks
        log.info("Reducing stock for item");
        Double totalAmount = 0.0;
        for(OrderRequestItemDto item : orderRequestDto.getItems()) {
            // Reduce stock logic here
            Long productId = item.getProductId();
            Integer quantity = item.getQuantity();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));
            if(product.getStock() < quantity) {
                throw new RuntimeException("Insufficient stock for product ID: " + productId);
            }
            product.setStock(product.getStock() - quantity);
            productRepository.save(product);
            totalAmount += product.getPrice() * quantity;
        }
        return totalAmount;
    }
}
