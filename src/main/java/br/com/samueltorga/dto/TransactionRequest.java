package br.com.samueltorga.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

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
            @JsonProperty("tx_count_24h") int txCount24h,
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