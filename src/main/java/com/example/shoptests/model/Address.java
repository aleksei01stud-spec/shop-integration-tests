package com.example.shoptests.model;

public record Address(String city, String street, String zipCode) {
    public boolean isValid() {
        return city != null && !city.isBlank()
                && street != null && street.length() >= 3
                && zipCode != null && zipCode.matches("\\d{6}");
    }
}