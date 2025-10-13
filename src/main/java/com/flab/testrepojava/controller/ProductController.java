package com.flab.testrepojava.controller;


import com.flab.testrepojava.dto.ProductRequest;
import com.flab.testrepojava.dto.ProductResponse;
import com.flab.testrepojava.exception.OutOfStockException;
import com.flab.testrepojava.metrics.ApiRequestCounter;
import com.flab.testrepojava.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ApiRequestCounter apiRequestCounter;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll() {
        List<ProductResponse> products = productService.findAll();
        apiRequestCounter.increment(); // 요청 수 카운트 증가
        return ResponseEntity.ok(products);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> save(@RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.save(request));
    }

    @GetMapping("/search")
    public List<ProductResponse> search(@RequestParam("name") String name) {
        return productService.searchByName(name);
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/find")
    public ResponseEntity<ProductResponse> getByName(@RequestParam("name") String name) {
        return ResponseEntity.ok(productService.findByName(name));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable("id") Long id, @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    //낙관적락
    @PostMapping("/{id}/decrease")
    public ResponseEntity<String> decrease(@PathVariable("id") Long id, @RequestParam("amount") int amount) {
        productService.decreaseQuantity(id, amount);
        return ResponseEntity.ok("재고 감소 완료");
    }

    //비관적락
    @PostMapping("/{id}/decrease/pessimistic")
    public ResponseEntity<String> decreaseQuantityPessimistic(@PathVariable("id") Long id, @RequestParam("amount") int amount) {
        productService.decreaseQuantityWithPessimisticLock(id, amount);
        return ResponseEntity.ok("재고 차감 완료 (비관적 락)");
    }

    //Redisson 분산락
    @PostMapping("/{id}/decrease/redis")
    public ResponseEntity<String> decreaseQuantityWithRedis(@PathVariable("id") Long id, @RequestParam("amount") int amount) {
        try {
            productService.decreaseWithRedisLock(id, amount);
            return ResponseEntity.ok("재고 차감 완료 (Redis 락)");
        } catch (OutOfStockException e) {
            return ResponseEntity.ok("재고 부족");
        }
    }


}
