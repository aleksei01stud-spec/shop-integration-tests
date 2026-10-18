package com.example.shoptests.service;

import com.example.shoptests.model.Address;
import com.example.shoptests.model.Order;
import com.example.shoptests.model.PaymentDetails;
import com.example.shoptests.model.Product;
import com.example.shoptests.repository.OrderRepository;
import com.example.shoptests.repository.ProductRepository;

import java.util.List;


public class ShopFacade {

    public record SearchRequest(String query, String category,
                                Double minPrice, Double maxPrice,
                                SearchService.SortBy sortBy) {}

    public record CheckoutRequest(long userId, String email,
                                  PaymentDetails payment, Address address) {}

    private final SearchService search;
    private final CartService cart;
    private final OrderService orders;

    public ShopFacade(ProductRepository productRepo, OrderRepository orderRepo,
                      PaymentGateway gateway, EmailService email, LogService log) {
        this.search = new SearchService(productRepo);
        this.cart = new CartService(productRepo);
        this.orders = new OrderService(cart, productRepo, orderRepo, gateway, email, log);
    }

    public List<Product> search(SearchRequest req) {
        return search.search(req.query(), req.category(),
                req.minPrice(), req.maxPrice(), req.sortBy());
    }

    public void addToCart(long userId, long productId, int qty) {
        cart.addItem(userId, productId, qty);
    }

    public void applyPromoCode(long userId, String code) {
        cart.applyPromoCode(userId, code);
    }

    public double getCartTotal(long userId) {
        return cart.calculateTotal(userId);
    }

    public Order checkout(CheckoutRequest req) {
        return orders.checkout(req.userId(), req.email(),
                req.payment(), req.address());
    }
}