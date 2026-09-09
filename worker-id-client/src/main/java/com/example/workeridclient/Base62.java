package com.example.workeridclient;

/** Numeric Base62 using 0-9, a-z, A-Z. */
public final class Base62 {
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private Base62() { }

    public static String encode(long value) {
        if (value < 0) throw new IllegalArgumentException("only non-negative numbers are supported");
        if (value == 0) return "0";
        char[] result = new char[11];
        int index = result.length;
        while (value > 0) {
            result[--index] = ALPHABET[(int) (value % 62)];
            value /= 62;
        }
        return new String(result, index, result.length - index);
    }
}
