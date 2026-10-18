package com.example.shoptests.integration;

import com.example.shoptests.model.Order;
import com.example.shoptests.model.OrderStatus;
import com.example.shoptests.model.PaymentDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: оформление заказа")
class OrderIntegrationTest extends ShopTestBase {

    @Test
    @DisplayName("TC-01: сквозной сценарий покупки картой")
    void tc01_fullHappyPath() {
        cartService.addItem(1L, laptop.id(), 2);
        double expectedTotal = 50000.0 * 2 * 1.20;

        Order order = orderService.checkout(1L, "user@example.com", validCard(), validAddress());

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(expectedTotal, order.getTotal(), 0.01);
        assertEquals(1, emails.getSent().size(), "Должно быть одно письмо");

        // Locale.US — чтобы десятичный разделитель был точкой независимо от системы
        String expectedFragment = "total=" + String.format(Locale.US, "%.2f", expectedTotal);
        assertTrue(emails.getSent().get(0).body().contains(expectedFragment),
                "Email body: " + emails.getSent().get(0).body());

        assertTrue(orders.findById(order.getId()).isPresent());
        assertTrue(cartService.getOrCreate(1L).isEmpty(), "Корзина должна очиститься");
    }

    @Test
    @DisplayName("TC-08: оплата через PayPal")
    void tc08_paypal() {
        cartService.addItem(1L, phone.id(), 1);
        Order order = orderService.checkout(1L, "user@example.com",
                PaymentDetails.paypal("user@paypal.com"), validAddress());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(1, emails.getSent().size());
    }

    @Test
    @DisplayName("TC-11: отказ банка (decline-карта)")
    void tc11_bankDecline() {
        cartService.addItem(1L, laptop.id(), 1);
        PaymentDetails declined = PaymentDetails.card("4000000000000002", "12/30", "123");

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(1L, "user@example.com", declined, validAddress()));
        assertEquals(0, emails.getSent().size(), "Письмо не отправляется при отказе");
        assertEquals(1, orders.findAll().size());
        assertEquals(OrderStatus.FAILED, orders.findAll().get(0).getStatus());
    }
}