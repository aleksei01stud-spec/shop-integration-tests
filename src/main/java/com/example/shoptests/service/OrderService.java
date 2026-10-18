package com.example.shoptests.service;

import com.example.shoptests.model.*;
import com.example.shoptests.repository.OrderRepository;
import com.example.shoptests.repository.ProductRepository;

import java.util.List;

public class OrderService {
    private final CartService cartService;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final EmailService emailService;
    private final LogService logService;

    public OrderService(CartService cartService, ProductRepository products, OrderRepository orders,
                        PaymentGateway gateway, EmailService emailService, LogService logService) {
        this.cartService = cartService;
        this.products = products;
        this.orders = orders;
        this.gateway = gateway;
        this.emailService = emailService;
        this.logService = logService;
    }

    public Order checkout(long userId, String email, PaymentDetails payment, Address address) {
        Cart cart = cartService.getOrCreate(userId);
        if (cart.isEmpty()) {
            logService.log(LogService.Level.WARN, "checkout", userId, "empty_cart");
            throw new IllegalStateException("Cart is empty");
        }
        if (address == null || !address.isValid()) {
            logService.log(LogService.Level.WARN, "checkout", userId, "invalid_address");
            throw new IllegalArgumentException("Invalid address");
        }
        if (payment == null || !payment.isValid()) {
            logService.log(LogService.Level.WARN, "checkout", userId, "invalid_payment");
            throw new IllegalArgumentException("Invalid payment details");
        }

        for (CartItem item : cart.getItems()) {
            if (!products.decrementStock(item.getProduct().id(), item.getQuantity())) {
                logService.log(LogService.Level.ERROR, "checkout", userId,
                        "out_of_stock:" + item.getProduct().id());
                throw new IllegalStateException("Product out of stock: " + item.getProduct().name());
            }
        }

        double total = cartService.calculateTotal(userId);
        Order order = new Order(orders.nextId(), userId,
                List.copyOf(cart.getItems()), total, payment.getMethod(), address);
        orders.save(order);

        PaymentGateway.Result result = gateway.process(payment, total);
        switch (result) {
            case INVALID_DATA -> {
                order.setStatus(OrderStatus.FAILED);
                logService.log(LogService.Level.WARN, "payment", userId, "invalid_data");
                throw new IllegalArgumentException("Invalid payment data");
            }
            case DECLINED -> {
                order.setStatus(OrderStatus.FAILED);
                logService.log(LogService.Level.WARN, "payment", userId, "declined");
                throw new IllegalStateException("Payment declined by bank");
            }
            case SUCCESS -> {
                order.setStatus(OrderStatus.PAID);
                logService.log(LogService.Level.INFO, "payment", userId, "success");
                emailService.sendOrderConfirmation(email, order);
                logService.log(LogService.Level.INFO, "email", userId, "sent:" + email);
                cartService.clear(userId);
            }
        }
        logService.log(LogService.Level.INFO, "checkout", userId, "order:" + order.getId());
        return order;
    }
}