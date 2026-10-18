package com.example.shoptests.service;

import com.example.shoptests.model.Product;
import com.example.shoptests.repository.ProductRepository;

import java.util.*;
import java.util.stream.Collectors;

public class SearchService {
    private final ProductRepository products;

    public SearchService(ProductRepository products) { this.products = products; }

    public enum SortBy { RELEVANCE, PRICE_ASC, PRICE_DESC }

    public List<Product> search(String query, String category,
                                Double minPrice, Double maxPrice, SortBy sortBy) {
        List<Product> result = products.findAll().stream()
                .filter(p -> query == null || p.name().toLowerCase().contains(query.toLowerCase()))
                .filter(p -> category == null || p.category().equalsIgnoreCase(category))
                .filter(p -> minPrice == null || p.price() >= minPrice)
                .filter(p -> maxPrice == null || p.price() <= maxPrice)
                .collect(Collectors.toList());

        Comparator<Product> cmp = switch (sortBy == null ? SortBy.RELEVANCE : sortBy) {
            case RELEVANCE -> Comparator
                    .comparingInt((Product p) -> relevance(p, query)).reversed()
                    .thenComparingDouble(Product::price);
            case PRICE_ASC  -> Comparator.comparingDouble(Product::price);
            case PRICE_DESC -> Comparator.comparingDouble(Product::price).reversed();
        };
        result.sort(cmp);
        return result;
    }

    private int relevance(Product p, String q) {
        if (q == null) return 0;
        String name = p.name().toLowerCase(), query = q.toLowerCase();
        if (name.equals(query)) return 100;
        if (name.startsWith(query)) return 50;
        return 10;
    }
}