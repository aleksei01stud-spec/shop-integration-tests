package com.example.shoptests.service;

import com.example.shoptests.model.Order;

import java.util.*;

public class EmailService {
    public record Email(String to, String subject, String body) {}

    private final List<Email> sent = Collections.synchronizedList(new ArrayList<>());

    public void sendOrderConfirmation(String to, Order order) {
        String body = String.format(Locale.US,
                "Order #%d, total=%.2f, items=%d, address=%s",
                order.getId(), order.getTotal(), order.getItems().size(), order.getAddress());
        sent.add(new Email(to, "Order confirmation #" + order.getId(), body));
    }

    public List<Email> getSent() { return List.copyOf(sent); }
    public void clear() { sent.clear(); }
}