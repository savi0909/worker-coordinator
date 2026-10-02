package com.example.workerclient;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Minimal RFC 9562 UUIDv7 generator for request idempotency keys.
 */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID random() {
        long timestamp = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long most = (timestamp << 16) | 0x7000 | RANDOM.nextInt(0x1000);
        long least = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(most, least);
    }

    public static boolean isV7(UUID value) {
        return value != null && value.version() == 7 && value.variant() == 2;
    }
}
