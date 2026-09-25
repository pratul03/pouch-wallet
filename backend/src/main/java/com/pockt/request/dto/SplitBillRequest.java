package com.pockt.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SplitBillRequest(
    @NotBlank(message = "Title is required for split bill")
    @Size(max = 120, message = "Title cannot exceed 120 characters")
    String title,

    @NotEmpty(message = "Must have at least one participant to split with")
    List<@Valid ParticipantSplit> splits,

    Integer expiryHours
) {
    public record ParticipantSplit(
        String phone,
        String vpa,

        @Positive(message = "Split amount must be greater than zero")
        long amountCents,

        String note
    ) {
        public boolean hasTarget() {
            return (phone != null && !phone.isBlank()) || (vpa != null && !vpa.isBlank());
        }
    }
}
