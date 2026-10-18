package com.example.shoptests.integration;

import com.example.shoptests.model.Address;
import com.example.shoptests.model.PaymentDetails;
import com.example.shoptests.model.Product;
import com.example.shoptests.repository.OrderRepository;
import com.example.shoptests.repository.ProductRepository;
import com.example.shoptests.service.*;
import org.junit.jupiter.api.BeforeEach;

public abstract class ShopTestBase {
    protected ProductRepository products;
    protected OrderRepository orders;
    protected LogService logs;
    protected EmailService emails;
    protected PaymentGateway gateway;
    protected SearchService search;
    protected CartService cartService;
    protected OrderService orderService;

    protected Product laptop;
    protected Product phone;

    @BeforeEach
    void setUp() {
        products = new ProductRepository();
        orders = new OrderRepository();
        logs = new LogService();
        emails = new EmailService();
        gateway = new PaymentGateway();
        search = new SearchService(products);
        cartService = new CartService(products);
        orderService = new OrderService(cartService, products, orders, gateway, emails, logs);

        laptop = products.save("Ноутбук ASUS", "Электроника", 50000, 10);
        phone = products.save("iPhone 15", "Электроника", 80000, 5);
    }

    protected Address validAddress() { return new Address("Москва", "Тверская 1", "101000"); }
    protected PaymentDetails validCard() { return PaymentDetails.card("4111111111111111", "12/30", "123"); }
}