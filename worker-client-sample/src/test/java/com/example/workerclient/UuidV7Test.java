package com.example.workerclient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UuidV7Test {
    @Test
    void generatedKeyIsUuidV7AndUnique() {
        var first = UuidV7.random();
        var second = UuidV7.random();
        assertTrue(UuidV7.isV7(first));
        assertTrue(UuidV7.isV7(second));
        assertNotEquals(first, second);
    }
}
