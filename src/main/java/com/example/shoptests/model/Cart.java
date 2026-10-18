package com.example.shoptests.model;

import java.util.*;

public class Cart {
    private final long userId;
    private final Map<Long, CartItem> items = new LinkedHashMap<>();
    private String promoCode;

    public Cart(long userId) { this.userId = userId; }

    public void addItem(Product product, int quantity) {
        items.merge(product.id(),
                new CartItem(product, quantity),
                (existing, added) -> {
                    existing.setQuantity(existing.getQuantity() + added.getQuantity());
                    return existing;
                });
    }

    public void updateQuantity(long productId, int quantity) {
        CartItem item = items.get(productId);
        if (item == null) throw new NoSuchElementException("Item not in cart");
        item.setQuantity(quantity);
    }

    public void removeItem(long productId) {
        if (items.remove(productId) == null) throw new NoSuchElementException("Item not in cart");
    }

    public Collection<CartItem> getItems() { return items.values(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public long getUserId() { return userId; }
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
    public void clear() { items.clear(); promoCode = null; }
}