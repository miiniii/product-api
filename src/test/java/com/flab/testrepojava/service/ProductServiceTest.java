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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private RedisLockService redisLockService;
    private ProductMapper productMapper;
    private SlackNotifier slackNotifier;
    private RetryMetricsService retryMetricsService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        redisLockService = mock(RedisLockService.class);
        productMapper = mock(ProductMapper.class);
        slackNotifier = mock(SlackNotifier.class);
        retryMetricsService = mock(RetryMetricsService.class);

        productService = new ProductService(
                productRepository,
                redisLockService,
                productMapper,
                slackNotifier,
                retryMetricsService
        );
    }

    @Test
    @DisplayName("save - 정상 요청이면 상품 저장 후 응답 반환")
    void save_success() {
        ProductRequest request = new ProductRequest("콜라", 1500, 10);
        Product product = new Product();
        Product saved = new Product();
        ProductResponse response = new ProductResponse(1L, "콜라", 1500, 10);

        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(saved);
        when(productMapper.toResponse(saved)).thenReturn(response);

        ProductResponse result = productService.save(request);

        assertThat(result).isEqualTo(response);
        verify(productMapper).toEntity(request);
        verify(productRepository).save(product);
        verify(productMapper).toResponse(saved);
    }

    @Test
    @DisplayName("save - 이름이 null이면 예외")
    void save_fail_nameIsNull() {
        ProductRequest request = new ProductRequest(null, 1500, 10);

        assertThatThrownBy(() -> productService.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 이름은 필수입니다.");
    }

    @Test
    @DisplayName("save - 이름이 공백이면 예외")
    void save_fail_nameIsBlank() {
        ProductRequest request = new ProductRequest("   ", 1500, 10);

        assertThatThrownBy(() -> productService.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 이름은 필수입니다.");
    }

    @Test
    @DisplayName("save - 가격이 null이면 예외")
    void save_fail_priceIsBlank() {
        ProductRequest request = new ProductRequest("콜라", null, 10);

        assertThatThrownBy(() -> productService.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 가격은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("save - 가격이 0이하면 예외")
    void save_fail_priceUnderZero() {
        ProductRequest request = new ProductRequest("콜라", 0, 10);

        assertThatThrownBy(() -> productService.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 가격은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("save - 수량이 null이면 예외")
    void save_fail_quantityIsNull() {
        ProductRequest request = new ProductRequest("콜라", 1500, null);

        assertThatThrownBy(() -> productService.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 수량은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("save - 수량이 0이하면 예외")
    void save_fail_quantityUnderZero() {
        ProductRequest request = new ProductRequest("콜라", 1500, 0);

        assertThatThrownBy(() -> productService.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 수량은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("findById - 상품이 있으면 응답 반환")
    void findById_success() {
        Long id = 1L;
        Product product = new Product();
        ProductResponse response = new ProductResponse(1L, "콜라", 1500, 10);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        ProductResponse result = productService.findById(id);

        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("findById - 상품이 없으면 예외")
    void findById_fail() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product not found");
    }

    @Test
    @DisplayName("update - 상품이 있으면 수정 후 응답 반환")
    void update_success() {
        Long id = 1L;
        ProductRequest request = new ProductRequest("콜라", 1500, 5);
        Product product = new Product();
        ProductResponse response = new ProductResponse(1L, "콜라", 1500, 5);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        ProductResponse result = productService.update(id, request);

        assertThat(result).isEqualTo(response);
        verify(productMapper).updateFromDto(request, product);
        verify(productRepository).save(product);
    }

    //update - 조회 실패 케이스도 넣기

    @Test
    @DisplayName("delete - 상품이 존재하면 삭제")
    void delete_success() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.delete(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete - 상품이 없으면 예외")
    void delete_fail() {
        when(productRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제할 상품이 존재하지 않습니다.");
    }

    @Test
    @DisplayName("searchByName - 검색어로 상품 목록 조회")
    void searchByName_success() {
        String keyword = "콜";
        Product p1 = new Product();
        Product p2 = new Product();
        ProductResponse r1 = new ProductResponse(1L, "콜라", 1500, 10);
        ProductResponse r2 = new ProductResponse(2L, "제로 콜라", 1700, 8);

        when(productRepository.findByNameContaining(keyword)).thenReturn(List.of(p1, p2));
        when(productMapper.toResponse(p1)).thenReturn(r1);
        when(productMapper.toResponse(p2)).thenReturn(r2);

        List<ProductResponse> result = productService.searchByName(keyword);

        assertThat(result).containsExactly(r1, r2);
    }

    @Test
    @DisplayName("searchByName - 검색어가 비어있으면 예외")
    void searchByName_fail_whenBlank() {
        assertThatThrownBy(() -> productService.searchByName("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("검색어는 비어 있을 수 없습니다.");
    }

    @Test
    @DisplayName("decreaseQuantity - 정상 차감")
    void decreaseQuantity_success() {
        Product product = new Product();
        product.setQuantity(10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.decreaseQuantity(1L, 2);

        assertThat(product.getQuantity()).isEqualTo(8);
        verify(productRepository).save(product);
        verifyNoInteractions(slackNotifier);
    }

    @Test
    @DisplayName("decreaseQuantity - 상품이 없으면 예외")
    void decreaseQuantity_fail_whenProductNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.decreaseQuantity(1L, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product not found");
    }

    @Test
    @DisplayName("decreaseQuantity - save 중 optimistic lock 예외 발생 시 retry metric 기록")
    void decreaseQuantity_optimisticLockCaught() {
        Product product = new Product();
        product.setQuantity(10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        doThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L))
                .when(productRepository).save(product);

        productService.decreaseQuantity(1L, 2);

        assertThat(product.getQuantity()).isEqualTo(8);
        verify(retryMetricsService).countRetryAttempt(any(ObjectOptimisticLockingFailureException.class));
    }

    @Test
    @DisplayName("decreaseQuantity - 재고 부족이면 슬랙 알림 후 예외")
    void decreaseQuantity_fail_whenOutOfStock() {
        Product product = new Product();
        product.setQuantity(2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseQuantity(1L, 5))
                .isInstanceOf(OutOfStockException.class)
                .hasMessage("재고가 부족합니다.");

        verify(slackNotifier).queueMessage(contains("재고 부족"));
    }

    @Test
    @DisplayName("recover - 슬랙 알림 전송")
    void recover_success() {
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException(Product.class, 1L);

        productService.recover(ex, 1L, 2);

        verify(slackNotifier).queueMessage(contains("낙관적 락 재시도 실패"));
    }

    @Test
    @DisplayName("decreaseQuantityWithPessimisticLock - 정상 차감")
    void decreaseQuantityWithPessimisticLock_success() {
        Product product = new Product();
        product.setQuantity(10);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        productService.decreaseQuantityWithPessimisticLock(1L, 2);

        assertThat(product.getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("decreaseQuantityWithPessimisticLock - 재고 부족이면 예외")
    void  decreaseQuantityWithPessimisticLock_fail_whenOutOfStock() {
        Product product = new Product();
        product.setQuantity(2);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseQuantityWithPessimisticLock(1L, 5))
                .isInstanceOf(OutOfStockException.class)
                .hasMessage("재고가 부족합니다.");

        verify(slackNotifier).queueMessage(contains("[PESSIMISTIC] 재고 부족"));
    }

    @Test
    @DisplayName("decreaseWithRedisLock - 정상 차감")
    void decreaseWithRedisLock_success() {
        Product product = new Product();
        product.setQuantity(10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Void> supplier = invocation.getArgument(4);
            return supplier.get();
        }).when(redisLockService).executeWithLock(
                eq("lock:product:1"),
                eq(1L),
                eq(3L),
                eq(TimeUnit.SECONDS),
                any()
        );

        productService.decreaseWithRedisLock(1L, 2);

        assertThat(product.getQuantity()).isEqualTo(8);
        verify(productRepository).save(product);
    }
}

