package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Product;
import com.flab.testrepojava.dto.ProductRequest;
import com.flab.testrepojava.dto.ProductResponse;
import com.flab.testrepojava.mapper.ProductMapper;
import com.flab.testrepojava.repository.ProductRepository;
import net.bytebuddy.implementation.bytecode.Throw;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 ID로 조회하면 존재하는 상품은 정보를 반환한다.")
    void 존재하는상품_정상조회() {
        //given
        Product product = createProduct(1L, "사과", 1000,5);

        ProductResponse response = createProductResponse(1L, "사과", 1000, 5);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productMapper.toResponse(product)).willReturn(response);

        //when
        ProductResponse result = productService.findById(1L);

        //then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("사과");
        assertThat(result.getPrice()).isEqualTo(1000);
        assertThat(result.getQuantity()).isEqualTo(5);

    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 조회하면 예외를 발생시킨다.")
    void 없는상품조회_예외발생() {
        // given
        given(productRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThrows(IllegalArgumentException.class, () -> productService.findById(999L));
    }

    @Test
    @DisplayName("정상적인 상품 요청을 전달하면 상품 등록 후 응답을 반환한다.")
    void 상품을_정상적으로_저장() {
        //given
        ProductRequest request = createProductRequest("바나나", 2000, 10);
        Product productEntity = createProduct(null, "바나나", 2000, 10);
        Product savedEntity = createProduct(1L, "바나나", 2000, 10);
        ProductResponse expectedResponse = createProductResponse(1L, "바나나", 2000, 10);

        given(productMapper.toEntity(request)).willReturn(productEntity);
        given(productRepository.save(productEntity)).willReturn(savedEntity);
        given(productMapper.toResponse(savedEntity)).willReturn(expectedResponse);

        //when
        ProductResponse result = productService.save(request);

        //then
        assertThat(result)
                .extracting("id", "name", "price", "quantity")
                .containsExactly(1L,"바나나", 2000, 10);

    }

    @Test
    @DisplayName("가격이 0원인 상품을 등록시 예외 발생")
    void 가격0원_상품등록_예외발생() {
        //given
        ProductRequest request = createProductRequest("무료나눔", 0, 10);

        //when
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.save(request);
        });

        //then
        assertThat(exception.getMessage()).contains("상품 가격은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("수량이 0인 상품을 등록시 예외 발생")
    void 수량0인_상품등록_예외발생() {
        //given
        ProductRequest request = createProductRequest("재고없음", 10, 0);

        //when
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.save(request);
        });

        //then
        assertThat(exception.getMessage()).contains("상품 수량은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("이름이 공백인 상품을 등록시 예외 발생")
    void 이름공백_상품등록_예외발생() {
        //given
        ProductRequest request = createProductRequest("     ", 1000,10);

        //when
        Throwable exception = assertThrows(IllegalArgumentException.class, () ->{
            productService.save(request);
        });

        //then
        assertThat(exception.getMessage()).contains("상품 이름은 필수입니다.");
    }

    //현재 DB에는 상품명 중복으로 들어가있음
//    @Test
//    @DisplayName("중복된 상품명을 등록하면 예외가 발생한다.")
//    void 중복상품명_상품등록_예외발생() {
//        //given
//        ProductRequest request = createProductRequest("사과", 1000, 10);
//        Product existingProduct = createProduct(1L, "사과", 1000, 10);
//
//        given(productRepository.findByName("사과")).willReturn(Optional.of(existingProduct));
//
//        //when & then
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
//                () -> productService.save(request));
//
//        assertThat(exception.getMessage()).contains("이미 등록된 상품");
//    }

    @Test
    @DisplayName("가격이 null인 상품을 등록시 예외 발생")
    void 가격null_상품등록_예외발생() {
        //given
        ProductRequest request = createProductRequest("무료나눔", null, 10);

        //when
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.save(request);
        });

        //then
        assertThat(exception.getMessage()).contains("상품 가격은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("상품 수량이 null인 상품을 등록시 예외 발생")
    void 상품null_상품등록_예외발생() {
        //given
        ProductRequest request = createProductRequest("재고없음", 100, null);

        //when
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.save(request);
        });

        //then
        assertThat(exception.getMessage()).contains("상품 수량은 필수이며 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("정상적인 상품 수정 요청 시 정보가 업데이트")
    void 상품수정_정상() {
        //given
        Long productId = 1L;
        Product existingProduct = createProduct(productId, "기존 상품", 1000, 10);
        ProductRequest updateRequest =createProductRequest("수정 상품", 2000, 5);

        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        willDoNothing().given(productMapper).updateFromDto(updateRequest, existingProduct);
        given(productRepository.save(existingProduct)).willReturn(existingProduct);
        given(productMapper.toResponse(existingProduct)).willReturn(createProductResponse(productId, "수정 상품", 2000, 5));

        //when
        ProductResponse result = productService.update(productId, updateRequest);

        //then
        assertThat(result.getName()).isEqualTo("수정 상품");
        assertThat(result.getPrice()).isEqualTo(2000);
        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("존재하지 않는 상품ID로 수정 요청 시 예외가 발생")
    void 없는상품ID_수정요청_예외발생() {
        //given
        Long notExistId = 999L;
        ProductRequest request = createProductRequest("수정상품", 1000, 10);

        given(productRepository.findById(notExistId)).willReturn(Optional.empty());

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> productService.update(notExistId, request));

        //then
        assertThat(exception.getMessage()).contains("Product not found");
    }

    @Test
    @DisplayName("검색어가 빈 문자열일 경우 예외가 발생한다.")
    void 빈문자열_검색_예외발생() {
        //given
        String emptyKeyworkd = "   ";

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> productService.searchByName(emptyKeyworkd));

        //then
        assertThat(exception.getMessage()).contains("검색어는 비어 있을 수 없습니다.");
    }

    @Test
    @DisplayName("존재하는 상품 ID로 삭제 요청 시 정상적으로 삭제")
    void 정상_삭제() {
        //given
        Long id = 1L;
        given(productRepository.existsById(id)).willReturn(true);
        willDoNothing().given(productRepository).deleteById(id);

        //when
        productService.delete(id);

        //then
        verify(productRepository).deleteById(id);
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 삭제 요청 시 예외가 발생")
    void 없는ID_삭제_예외발생() {
        //given
        Long notExistId = 999L;
        given(productRepository.existsById(notExistId)).willReturn(false);

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> productService.delete(notExistId));

        //then
        assertThat(exception.getMessage()).contains("삭제할 상품이 존재하지 않습니다.");
    }

    @Test
    void 동시수정_테스트() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            service.submit(() -> {
                try {
                    productService.decreaseQuantity(1L, 1); // 같은 productId
                } catch (Exception e) {
                    System.out.println("예외 발생: " + e.getClass().getSimpleName());
                }
            });
        }

        service.shutdown();
        service.awaitTermination(5, TimeUnit.SECONDS);
    }



    //테스트용 헬퍼 메서드
    private ProductRequest createProductRequest(String name, Integer price, Integer quantity) {
        return ProductRequest.builder()
                .name(name)
                .price(price)
                .quantity(quantity)
                .build();
    }

    private Product createProduct(Long id, String name, Integer price, Integer quantity) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(price)
                .quantity(quantity)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductResponse createProductResponse(Long id, String name, Integer price, Integer quantity) {
        return ProductResponse.builder()
                .id(id)
                .name(name)
                .price(price)
                .quantity(quantity)
                .build();
    }
}

