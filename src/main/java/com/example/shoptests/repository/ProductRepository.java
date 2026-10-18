package com.example.shoptests.repository;

import com.example.shoptests.model.Product;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ProductRepository {
    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public Product save(String name, String category, double price, int stock) {
        long id = idGen.getAndIncrement();
        Product p = new Product(id, name, category, price, stock);
        products.put(id, p);
        return p;
    }

    public Optional<Product> findById(long id) { return Optional.ofNullable(products.get(id)); }
    public List<Product> findAll() { return new ArrayList<>(products.values()); }

    public synchronized boolean decrementStock(long productId, int qty) {
        Product p = products.get(productId);
        if (p == null || p.stock() < qty) return false;
        products.put(productId, p.withStock(p.stock() - qty));
        return true;
    }

    public void clear() { products.clear(); idGen.set(1); }
}