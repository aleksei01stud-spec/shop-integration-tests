package com.example.shoptests.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: конкурентность")
class ConcurrencyIntegrationTest extends ShopTestBase {

    @Test
    @DisplayName("TC-06/TC-12: одновременные заказы последней единицы товара")
    void tc06_lastItemRace() throws Exception {
        var rare = products.save("RareItem", "Электроника", 10000, 1);

        int users = 5;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < users; i++) {
            final long userId = 100L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    cartService.addItem(userId, rare.id(), 1);
                    orderService.checkout(userId, "u" + userId + "@ex.com",
                            validCard(), validAddress());
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, successes.get(), "успешным может быть только один заказ");
        assertEquals(users - 1, failures.get());
        assertEquals(0, products.findById(rare.id()).orElseThrow().stock(), "склад должен быть исчерпан");
    }
}