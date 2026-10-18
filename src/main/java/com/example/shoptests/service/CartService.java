package com.example.shoptests.service;

import com.example.shoptests.model.Cart;
import com.example.shoptests.model.CartItem;
import com.example.shoptests.model.Product;
import com.example.shoptests.repository.ProductRepository;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

public class CartService {
    public static final double TAX_RATE = 0.20;
    public static final double DISCOUNT_RATE = 0.10;
    public static final String DISCOUNT_CODE = "SALE10";

    private final Map<Long, Cart> carts = new ConcurrentHashMap<>();
    private final ProductRepository products;

    public CartService(ProductRepository products) { this.products = products; }

    public Cart getOrCreate(long userId) { return carts.computeIfAbsent(userId, Cart::new); }

    public void addItem(long userId, long productId, int quantity) {
        Product p = products.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Product not found"));
        getOrCreate(userId).addItem(p, quantity);
    }

    public void updateQuantity(long userId, long productId, int quantity) {
        getOrCreate(userId).updateQuantity(productId, quantity);
    }

    public void removeItem(long userId, long productId) {
        getOrCreate(userId).removeItem(productId);
    }

    public void applyPromoCode(long userId, String code) {
        getOrCreate(userId).setPromoCode(code);
    }

    public double calculateTotal(long userId) {
        Cart cart = getOrCreate(userId);
        double subtotal = cart.getItems().stream().mapToDouble(CartItem::subtotal).sum();
        if (DISCOUNT_CODE.equalsIgnoreCase(cart.getPromoCode())) subtotal *= (1 - DISCOUNT_RATE);
        return subtotal * (1 + TAX_RATE);
    }

    public void clear(long userId) { getOrCreate(userId).clear(); }
}