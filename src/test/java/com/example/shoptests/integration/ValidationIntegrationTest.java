package com.example.shoptests.integration;

import com.example.shoptests.model.Address;
import com.example.shoptests.model.PaymentDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: валидация")
class ValidationIntegrationTest extends ShopTestBase {

    @Test
    @DisplayName("TC-03: неверные данные карты — заказ не создан")
    void tc03_invalidCard() {
        cartService.addItem(1L, laptop.id(), 1);
        PaymentDetails bad = PaymentDetails.card("1234-5678", "01/20", "99");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(1L, "user@example.com", bad, validAddress()));
        assertEquals(1, cartService.getOrCreate(1L).getItems().size(), "корзина не изменена");
        assertEquals(0, emails.getSent().size());
    }

    @Test
    @DisplayName("TC-09: неверный адрес доставки")
    void tc09_invalidAddress() {
        cartService.addItem(1L, laptop.id(), 1);
        Address bad = new Address("", "A", "000");
        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(1L, "user@example.com", validCard(), bad));
    }

    @Test
    @DisplayName("Оформление пустой корзины")
    void emptyCartCheckout() {
        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(1L, "user@example.com", validCard(), validAddress()));
    }
}