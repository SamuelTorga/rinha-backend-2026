package br.com.samueltorga.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TransactionRequest(
        String id,
        Transaction transaction,
        Customer customer,
        Merchant merchant,
        Terminal terminal,
        LastTransaction lastTransaction
) {
    public record Transaction(
            double amount,
            int installments,
            String requestedAt
    ) {}

    public record Customer(
            double avgAmount,
            int txCount24h,
            List<String> knownMerchants
    ) {}

    public record Merchant(
            String id,
            String mcc,
            double avgAmount
    ) {}

    public record Terminal(
            @JsonProperty("is_online") boolean isOnline,
            boolean cardPresent,
            double kmFromHome
    ) {}

    public record LastTransaction(
            String timestamp,
            double kmFromCurrent
    ) {}
}