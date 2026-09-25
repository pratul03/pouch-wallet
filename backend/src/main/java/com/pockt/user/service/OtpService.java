package com.pockt.user.service;

import com.pockt.user.domain.OtpPurpose;

public interface OtpService {
    void send(String phone, OtpPurpose purpose);
    boolean verify(String phone, String otp, OtpPurpose purpose);
}
