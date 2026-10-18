package com.example.shoptests.model;

public record Product(long id, String name, String category, double price, int stock) {
    public Product withStock(int newStock) {
        return new Product(id, name, category, price, newStock);
    }
}
