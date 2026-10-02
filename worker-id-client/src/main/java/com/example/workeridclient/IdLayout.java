package com.example.workeridclient;

/**
 * Configurable positive signed-64-bit Snowflake-style layout.
 */
public record IdLayout(int timestampBits, int regionBits, int workerBits, int sequenceBits, long epochMillis) {
    public static final IdLayout DEFAULT = new IdLayout(41, 4, 10, 8, 1_704_067_200_000L);

    public IdLayout {
        if (timestampBits < 1 || regionBits < 1 || workerBits < 1 || sequenceBits < 1
                || timestampBits + regionBits + workerBits + sequenceBits > 63) {
            throw new IllegalArgumentException("layout must use between 4 and 63 positive bits");
        }
        if (epochMillis < 0) {
            throw new IllegalArgumentException("epochMillis must be non-negative");
        }
    }

    private static long mask(int bits) {
        return bits == 63 ? Long.MAX_VALUE : (1L << bits) - 1;
    }

    public long timestampMask() {
        return mask(timestampBits);
    }

    public long regionMask() {
        return mask(regionBits);
    }

    public long workerMask() {
        return mask(workerBits);
    }

    public long sequenceMask() {
        return mask(sequenceBits);
    }

    public int totalBits() {
        return timestampBits + regionBits + workerBits + sequenceBits;
    }

    public int regionShift() {
        return workerBits + sequenceBits;
    }

    public int workerShift() {
        return sequenceBits;
    }
}
