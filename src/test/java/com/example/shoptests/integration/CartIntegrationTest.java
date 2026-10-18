package com.example.shoptests.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: модуль корзины")
class CartIntegrationTest extends ShopTestBase {

    @Test
    @DisplayName("TC-02: изменение количества и удаление товара")
    void tc02_updateQuantityAndRemove() {
        cartService.addItem(1L, laptop.id(), 1);
        cartService.addItem(1L, phone.id(), 2);
        cartService.updateQuantity(1L, phone.id(), 5);
        cartService.removeItem(1L, laptop.id());

        double expected = 80000.0 * 5 * 1.20;
        assertEquals(expected, cartService.calculateTotal(1L), 0.01);
        assertEquals(1, cartService.getOrCreate(1L).getItems().size());
    }

    @Test
    @DisplayName("TC-02b: удаление отсутствующего товара")
    void tc02b_removeNonexistent() {
        assertThrows(NoSuchElementException.class,
                () -> cartService.removeItem(1L, laptop.id()));
    }

    @Test
    @DisplayName("TC-10: промокод SALE10 уменьшает итог на 10%")
    void tc10_discountApplied() {
        cartService.addItem(1L, laptop.id(), 1);
        assertEquals(50000 * 1.20, cartService.calculateTotal(1L), 0.01);

        cartService.applyPromoCode(1L, "SALE10");
        assertEquals(50000 * 0.9 * 1.20, cartService.calculateTotal(1L), 0.01);
    }
}
