package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Product;
import com.flab.testrepojava.dto.ProductRequest;
import com.flab.testrepojava.dto.ProductResponse;
import com.flab.testrepojava.exception.OutOfStockException;
import com.flab.testrepojava.interceptor.RetryMetricsService;
import com.flab.testrepojava.mapper.ProductMapper;
import com.flab.testrepojava.redis.RedisLockService;
import com.flab.testrepojava.repository.ProductRepository;
import com.flab.testrepojava.slack.SlackNotifier;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements ProductServiceImp {

    private final ProductRepository productRepository;
    private final RedisLockService redisLockService;
    private final ProductMapper productMapper;
    private final SlackNotifier slackNotifier;
    private final RetryMetricsService retryMetricsService;

    private static final AtomicBoolean stopLogging = new AtomicBoolean(false);

    @Override
    public ProductResponse save(ProductRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("상품 이름은 필수입니다.");
        }
        if (request.getPrice() == null || request.getPrice() <= 0) {
            throw new IllegalArgumentException("상품 가격은 필수이며 1 이상이어야 합니다.");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("상품 수량은 필수이며 1 이상이어야 합니다.");
        }
        Product product = productMapper.toEntity(request);
        Product saved = productRepository.save(product);
        return productMapper.toResponse(saved);
    }


    public List<ProductResponse> findAll() {
        List<Product> products = productRepository.findAll();
        return productMapper.toResponseList(products);
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        productMapper.updateFromDto(request, product);
        productRepository.save(product);

        return productMapper.toResponse(product);

    }

    @Override
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("삭제할 상품이 존재하지 않습니다.");
        }
        productRepository.deleteById(id);
    }

    //이름이 정확히 일치하는 상품 조회(캐시 없음)
    public ProductResponse findByName(String name) {
        Product product = productRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return productMapper.toResponse(product);
    }

    //이름에 일부가 포함된 상품 목록 조회(Redis 캐시 적용)
    @Cacheable(value = "productSearch", key = "#p0")
    public List<ProductResponse> searchByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }
        log.info(">> [CacheMiss] DB에서 검색 수행: {}", name);
        return productRepository.findByNameContaining(name).stream()
                .map(productMapper::toResponse)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "productSearch", key = "p0")
    public void evictSearchCache(String name) {
        log.info(">> 캐시 삭제: {}", name);
    }

    @Retryable(
            include = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 10)
    )
    @Transactional
    public void decreaseQuantity(Long productId, int amount) {
        log.info("▶️ 재고 감소 시작 - productId: {}, amount: {}", productId, amount);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        int before = product.getQuantity();
        product.setQuantity(before - amount);

        log.info("⏳ 저장 전 수량 감소 - 기존: {}, 차감 후: {}", before, product.getQuantity());

        try {
            productRepository.save(product); // ⛔️ 이 시점에서 버전 충돌 발생 가능
        } catch (ObjectOptimisticLockingFailureException e) {
            // 충돌 감지
            retryMetricsService.countRetryAttempt(e);
            log.error("Optimistic lock 실패: {}", e.getMessage());
        }

        // ⛔️ 저장 후 재고 부족이면 예외
        if (product.getQuantity() < 0) {
            String message = String.format(
                    "❌ 재고 부족 - 상품 ID: %d, 요청 수량: %d, 감소 후 수량: %d",
                    productId, amount, product.getQuantity()
            );
            log.warn("{}", message);
            slackNotifier.queueMessage(message);
            throw new OutOfStockException("재고가 부족합니다.");
        }

        log.info("✅ 재고 감소 완료 - productId: {}, 최종 수량: {}", productId, product.getQuantity());
    }

    // 🔁 낙관적 락 재시도 끝에도 실패할 경우 호출
    @Recover
    public void recover(ObjectOptimisticLockingFailureException e, Long productId, int amount) {
        String message = String.format(
                "🔁 낙관적 락 재시도 실패 - 상품 ID: %d, 요청 수량: %d, 에러: %s",
                productId, amount, e.getMessage()
        );
        log.error("🛑 Recover 실행됨 - {}", message);
        slackNotifier.queueMessage(message);

    }

    // 비관적 락 기반
    @Transactional
    public void decreaseQuantityWithPessimisticLock(Long productId, int amount) {
        log.info("▶️ [PESSIMISTIC] 재고 감소 시작 - productId: {}, amount: {}", productId, amount);

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        int before = product.getQuantity();
        int after = before - amount;

        if (after < 0) {
            String message = String.format("❌ [PESSIMISTIC] 재고 부족 - 상품 ID: %d, 요청: %d, 현재: %d", productId, amount, before);
            log.warn(message);
            slackNotifier.queueMessage(message);
            throw new OutOfStockException("재고가 부족합니다.");
        }

        product.setQuantity(after);

        log.info("✅ [PESSIMISTIC] 재고 차감 완료 - 기존: {}, 최종: {}", before, after);
    }

    // Redisson 분산락
    public void decreaseWithRedisLock(Long productId, int amount) {
        String lockKey = "lock:product:" + productId;

        redisLockService.executeWithLock(lockKey, 1, 3, TimeUnit.SECONDS, () -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (product.getQuantity() < amount) {
                slackNotifier.queueMessage("❌ Redis 재고 부족 - ID: " + productId);
                throw new OutOfStockException("재고 부족");
            }

            product.setQuantity(product.getQuantity() - amount);
            productRepository.save(product);

            log.info("🔐 Redis 락으로 재고 차감 완료 - id: {}, 남은 재고: {}", productId, product.getQuantity());
            return null;
        });
    }


}