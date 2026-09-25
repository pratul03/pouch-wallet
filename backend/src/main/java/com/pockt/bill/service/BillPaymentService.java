package com.pockt.bill.service;

import com.pockt.bill.dto.BillDetailsResponse;
import com.pockt.bill.dto.BillPaymentResponse;
import com.pockt.bill.dto.BillerCategoryResponse;
import com.pockt.bill.dto.BillerResponse;
import com.pockt.bill.dto.FetchBillRequest;
import com.pockt.bill.dto.PayBillRequest;

import java.util.List;
import java.util.UUID;

public interface BillPaymentService {
    List<BillerCategoryResponse> getCategories();
    List<BillerResponse> getBillers(String category);
    BillDetailsResponse fetchBill(FetchBillRequest request);
    BillPaymentResponse payBill(UUID userId, PayBillRequest request);
    List<BillPaymentResponse> getUserBillPayments(UUID userId);
}
