package com.example.shoptests.model;

import java.util.List;

public class Order {
    private final long id;
    private final long userId;
    private final List<CartItem> items;
    private final double total;
    private OrderStatus status;
    private final PaymentMethod paymentMethod;
    private final Address address;

    public Order(long id, long userId, List<CartItem> items, double total,
                 PaymentMethod paymentMethod, Address address) {
        this.id = id;
        this.userId = userId;
        this.items = List.copyOf(items);
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.address = address;
        this.status = OrderStatus.PENDING;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public List<CartItem> getItems() { return items; }
    public double getTotal() { return total; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus s) { this.status = s; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public Address getAddress() { return address; }
}