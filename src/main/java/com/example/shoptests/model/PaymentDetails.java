package com.example.shoptests.model;

public class PaymentDetails {
    private final PaymentMethod method;
    private final String cardNumber;
    private final String expiry;
    private final String cvv;
    private final String paypalEmail;

    public static PaymentDetails card(String number, String expiry, String cvv) {
        return new PaymentDetails(PaymentMethod.CREDIT_CARD, number, expiry, cvv, null);
    }

    public static PaymentDetails paypal(String email) {
        return new PaymentDetails(PaymentMethod.PAYPAL, null, null, null, email);
    }

    private PaymentDetails(PaymentMethod method, String cardNumber, String expiry,
                           String cvv, String paypalEmail) {
        this.method = method;
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.cvv = cvv;
        this.paypalEmail = paypalEmail;
    }

    public boolean isValid() {
        if (method == PaymentMethod.CREDIT_CARD) {
            return cardNumber != null && cardNumber.replaceAll("\\s", "").matches("\\d{16}")
                    && expiry != null && expiry.matches("(0[1-9]|1[0-2])/\\d{2}")
                    && cvv != null && cvv.matches("\\d{3}");
        }
        return paypalEmail != null && paypalEmail.contains("@");
    }

    public PaymentMethod getMethod() { return method; }
    public String getCardNumber() { return cardNumber; }
    public String getPaypalEmail() { return paypalEmail; }
}