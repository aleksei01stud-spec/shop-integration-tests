package com.example.shoptests.service;

import com.example.shoptests.model.PaymentDetails;
import com.example.shoptests.model.PaymentMethod;

public class PaymentGateway {
    public enum Result { SUCCESS, DECLINED, INVALID_DATA }

    public Result process(PaymentDetails details, double amount) {
        if (details == null || !details.isValid()) return Result.INVALID_DATA;
        if (details.getMethod() == PaymentMethod.CREDIT_CARD
                && "4000000000000002".equals(details.getCardNumber().replaceAll("\\s", ""))) {
            return Result.DECLINED;
        }
        return Result.SUCCESS;
    }
}