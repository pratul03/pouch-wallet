package com.pockt.user.event;

import java.util.UUID;

public record UserRegisteredEvent(
    UUID userId,
    String phone
) {}
