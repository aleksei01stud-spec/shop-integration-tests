package com.example.shoptests.repository;

import com.example.shoptests.model.Order;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class OrderRepository {
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public Order save(Order order) { orders.put(order.getId(), order); return order; }
    public Optional<Order> findById(long id) { return Optional.ofNullable(orders.get(id)); }
    public List<Order> findAll() { return new ArrayList<>(orders.values()); }
    public long nextId() { return idGen.getAndIncrement(); }
    public void clear() { orders.clear(); idGen.set(1); }
}