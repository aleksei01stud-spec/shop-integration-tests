package com.example.shoptests.integration;

import com.example.shoptests.model.*;
import com.example.shoptests.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("КТ-4: Интеграционное тестирование методом «Большого взрыва»")
class BigBangIntegrationTest extends ShopTestBase {


    @Test
    @DisplayName("BB-01: сквозной путь пользователя через все модули")
    void bb01_fullSystemIntegration() {
        products.save("Наушники Sony", "Электроника", 15000, 20);
        products.save("Монитор LG",    "Электроника", 25000, 3);
        products.save("Пылесос",       "Бытовая техника", 8000, 5);

        List<Product> found = search.search("Sony", "Электроника",
                10000.0, 20000.0, SearchService.SortBy.PRICE_ASC);
        assertEquals(1, found.size(), "Поиск должен найти 1 товар");
        Product sony = found.get(0);

        cartService.addItem(1L, sony.id(), 2);
        cartService.addItem(1L, laptop.id(), 1);
        cartService.applyPromoCode(1L, "SALE10");

        double expectedSubtotal = (15000 * 2) + 50000;
        double expectedAfterDiscount = expectedSubtotal * 0.9;
        double expectedTotal = expectedAfterDiscount * 1.20;
        assertEquals(expectedTotal, cartService.calculateTotal(1L), 0.01,
                "Итог корзины с учётом скидки и налога");

        Order order = orderService.checkout(1L, "client@shop.com",
                validCard(), validAddress());

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(expectedTotal, order.getTotal(), 0.01);
        assertTrue(orders.findById(order.getId()).isPresent());

        assertEquals(18, products.findById(sony.id()).orElseThrow().stock());
        assertEquals(9,  products.findById(laptop.id()).orElseThrow().stock());

        assertEquals(1, emails.getSent().size());
        assertTrue(emails.getSent().get(0).to().equals("client@shop.com"));
        assertTrue(emails.getSent().get(0).body().contains("total=86400.00"));

        var logEntries = logs.getEntries();
        assertTrue(logEntries.stream().anyMatch(e ->
                e.operation().equals("payment") && e.result().equals("success")));
        assertTrue(logEntries.stream().anyMatch(e -> e.operation().equals("email")));
        assertTrue(logEntries.stream().anyMatch(e -> e.operation().equals("checkout")));

        assertTrue(cartService.getOrCreate(1L).isEmpty());
    }


    @Test
    @DisplayName("BB-02: сквозной негативный путь — отказ банка")
    void bb02_fullNegativePath() {
        List<Product> found = search.search("iPhone", null, null, null, null);
        assertEquals(1, found.size());

        cartService.addItem(2L, phone.id(), 1);

        PaymentDetails declined = PaymentDetails.card("4000000000000002", "12/30", "123");
        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(2L, "bad@shop.com", declined, validAddress()));

        assertEquals(0, emails.getSent().size(), "email не должен быть отправлен");
        assertEquals(OrderStatus.FAILED, orders.findAll().get(0).getStatus());
        assertEquals(1, cartService.getOrCreate(2L).getItems().size(),
                "корзина НЕ должна очищаться при неуспехе");

        var logEntries = logs.getEntries();
        assertTrue(logEntries.stream().anyMatch(e ->
                e.operation().equals("payment") && e.result().equals("declined")));
    }


    @Test
    @DisplayName("BB-03: все модули под нагрузкой (Big Bang + concurrency)")
    void bb03_allModulesUnderConcurrency() throws Exception {
        int users = 10;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(users);
        var start = new java.util.concurrent.CountDownLatch(1);
        var ok = new java.util.concurrent.atomic.AtomicInteger();
        var err = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < users; i++) {
            final long uid = 200L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    search.search("ASUS", null, null, null, null);
                    cartService.addItem(uid, laptop.id(), 1);
                    orderService.checkout(uid, "u" + uid + "@shop.com",
                            validCard(), validAddress());
                    ok.incrementAndGet();
                } catch (Exception e) {
                    err.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(0, products.findById(laptop.id()).orElseThrow().stock());
        assertEquals(10, ok.get(), "Все 10 заказов должны пройти");
        assertEquals(0, err.get());
        assertEquals(10, emails.getSent().size(), "Каждому — письмо");
        assertEquals(10, orders.findAll().size(), "Каждый заказ сохранён");
    }
}