package com.example.shoptests.integration;

import com.example.shoptests.model.PaymentDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: логирование")
class LoggingIntegrationTest extends ShopTestBase {

    @Test
    @DisplayName("TC-07: успешные и неуспешные операции попадают в лог")
    void tc07_allOperationsLogged() {
        cartService.addItem(1L, laptop.id(), 1);
        orderService.checkout(1L, "u@ex.com", validCard(), validAddress());


        cartService.addItem(2L, laptop.id(), 1);
        PaymentDetails declined = PaymentDetails.card("4000000000000002", "12/30", "123");
        assertThrows(RuntimeException.class,
                () -> orderService.checkout(2L, "u2@ex.com", declined, validAddress()));

        var entries = logs.getEntries();
        assertTrue(entries.stream().anyMatch(e ->
                        e.operation().equals("payment") && e.result().equals("success")),
                "должна быть запись об успешной оплате");
        assertTrue(entries.stream().anyMatch(e ->
                        e.operation().equals("payment") && e.result().equals("declined")),
                "должна быть запись об отказе банка");
        assertTrue(entries.stream().anyMatch(e -> e.operation().equals("email")),
                "должна быть запись об отправке письма");
        assertTrue(entries.stream().anyMatch(e -> e.operation().equals("checkout")),
                "должна быть запись о checkout");
    }
}