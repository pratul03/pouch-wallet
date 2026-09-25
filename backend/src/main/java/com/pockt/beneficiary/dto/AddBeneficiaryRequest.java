package com.pockt.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(
    @NotBlank(message = "Beneficiary name is required")
    @Size(max = 120, message = "Name cannot exceed 120 characters")
    String name,

    @Size(max = 60, message = "Nickname cannot exceed 60 characters")
    String nickname,

    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    String phone,

    @Size(max = 60, message = "VPA cannot exceed 60 characters")
    String vpa,

    @Size(max = 30, message = "Account number cannot exceed 30 characters")
    String accountNumber,

    @Size(max = 20, message = "IFSC code cannot exceed 20 characters")
    String ifscCode,

    Boolean isFavorite
) {
    public boolean isValidTarget() {
        return (phone != null && !phone.isBlank())
                || (vpa != null && !vpa.isBlank())
                || (accountNumber != null && !accountNumber.isBlank() && ifscCode != null && !ifscCode.isBlank());
    }
}
