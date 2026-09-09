package com.example.workeridclient;

import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Thread-safe, fail-closed ID generator guarded by the current local lease. */
public final class LeaseAwareIdGenerator {
    private final IdLayout layout;
    private final LongSupplier clockMillis;
    private final Sleeper sleeper;
    private LeaseSnapshot lease;
    private long lastTimestamp = -1;
    private long sequence;
    private boolean fenced;

    public LeaseAwareIdGenerator(IdLayout layout) {
        this(layout, System::currentTimeMillis, millis -> Thread.sleep(millis));
    }

    public LeaseAwareIdGenerator(IdLayout layout, LongSupplier clockMillis, Sleeper sleeper) {
        this.layout = Objects.requireNonNull(layout);
        this.clockMillis = Objects.requireNonNull(clockMillis);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    public synchronized void installLease(LeaseSnapshot candidate) {
        Objects.requireNonNull(candidate);
        validateSlot(candidate);
        if (fenced) throw new LeaseUnavailableException("ID generation is fenced");
        if (lease != null && candidate.epoch() < lease.epoch()) {
            fenced = true;
            lease = null;
            throw new LeaseUnavailableException("stale lease epoch");
        }
        lease = candidate;
    }

    public synchronized void fence() {
        fenced = true;
        lease = null;
    }

    public synchronized long nextLong() {
        ensureLease();
        long now = clockMillis.getAsLong();
        if (lastTimestamp >= 0 && now < lastTimestamp) {
            fenced = true;
            lease = null;
            throw new ClockRollbackException(lastTimestamp, now);
        }
        if (now == lastTimestamp) {
            sequence++;
            if (sequence > layout.sequenceMask()) {
                do {
                    sleepOneMillisecond();
                    now = clockMillis.getAsLong();
                    if (now < lastTimestamp) {
                        fenced = true;
                        lease = null;
                        throw new ClockRollbackException(lastTimestamp, now);
                    }
                } while (now <= lastTimestamp);
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
        if (now - layout.epochMillis() < 0 || now - layout.epochMillis() > layout.timestampMask()) {
            throw new IllegalStateException("timestamp does not fit configured layout");
        }
        ensureLease();
        lastTimestamp = now;
        return ((now - layout.epochMillis()) << (layout.regionBits() + layout.workerBits() + layout.sequenceBits()))
                | ((long) lease.regionId() << layout.regionShift())
                | ((long) lease.workerId() << layout.workerShift())
                | sequence;
    }

    public String nextBase62() { return Base62.encode(nextLong()); }

    public synchronized boolean isGenerationAllowed() {
        return !fenced && lease != null && Instant.ofEpochMilli(clockMillis.getAsLong()).isBefore(lease.leaseExpiry());
    }

    private void ensureLease() {
        if (fenced || lease == null) throw new LeaseUnavailableException("no currently valid lease");
        if (!Instant.ofEpochMilli(clockMillis.getAsLong()).isBefore(lease.leaseExpiry())) {
            fenced = true;
            lease = null;
            throw new LeaseUnavailableException("lease expired");
        }
    }

    private void validateSlot(LeaseSnapshot candidate) {
        if (candidate.regionId() < 0 || candidate.regionId() > layout.regionMask()
                || candidate.workerId() < 0 || candidate.workerId() > layout.workerMask()) {
            throw new IllegalArgumentException("lease slot does not fit configured layout");
        }
    }

    private void sleepOneMillisecond() {
        try { sleeper.sleep(1); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LeaseUnavailableException("generation interrupted while waiting for sequence capacity");
        }
    }

    @FunctionalInterface
    public interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
