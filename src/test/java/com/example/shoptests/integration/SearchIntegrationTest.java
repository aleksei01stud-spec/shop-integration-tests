package com.example.shoptests.integration;

import com.example.shoptests.model.Product;
import com.example.shoptests.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: модуль поиска")
class SearchIntegrationTest extends ShopTestBase {

    @Test
    @DisplayName("TC-04: фильтры категория+цена + сортировка по цене")
    void tc04_searchWithFiltersAndSort() {
        products.save("Пылесос", "Бытовая техника", 8000, 5);
        products.save("Наушники Sony", "Электроника", 15000, 20);
        products.save("Монитор LG", "Электроника", 25000, 3);
        products.save("Флагман", "Электроника", 200000, 1);

        List<Product> result = search.search(null, "Электроника", 5000.0, 30000.0,
                SearchService.SortBy.PRICE_ASC);

        // В диапазон 5000-30000 из "Электроники" попадают ровно 2 товара:
        //   Наушники Sony (15000) и Монитор LG (25000).
        assertEquals(2, result.size(),
                "Ожидалось 2 товара, но получено: " +
                        result.stream().map(Product::name).toList());
        assertEquals("Наушники Sony", result.get(0).name());
        assertEquals("Монитор LG", result.get(1).name());
        result.forEach(p -> {
            assertTrue(p.price() >= 5000 && p.price() <= 30000,
                    "Цена вне диапазона: " + p.name() + " = " + p.price());
            assertEquals("Электроника", p.category());
        });
    }

    @Test
    @DisplayName("TC-04b: сортировка по релевантности — точное совпадение выше")
    void tc04b_relevanceSort() {
        products.save("iPhone", "Электроника", 70000, 3);
        products.save("iPhone 15 Pro", "Электроника", 120000, 2);

        List<Product> result = search.search("iPhone", null, null, null,
                SearchService.SortBy.RELEVANCE);
        assertEquals("iPhone", result.get(0).name(),
                "Первым должен быть точный match. Получено: " +
                        result.stream().map(Product::name).toList());
    }

    @Test
    @DisplayName("TC-05: поиск не находит ничего")
    void tc05_emptyResult() {
        List<Product> result = search.search(null, null, 1_000_000.0, null, null);
        assertTrue(result.isEmpty());
    }
}