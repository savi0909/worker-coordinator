package com.example.workeridclient;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LeaseAwareIdGeneratorTest {
    private static final Instant EXPIRY = Instant.ofEpochMilli(2_000_000);

    @Test
    void generatesUniqueIdsAndPreservesConfiguredFields() {
        AtomicLong clock = new AtomicLong(1_000_000);
        IdLayout layout = new IdLayout(40, 4, 10, 9, 0);
        LeaseAwareIdGenerator generator = new LeaseAwareIdGenerator(layout, clock::get, ignored -> { });
        generator.installLease(new LeaseSnapshot(3, 17, 4, EXPIRY));

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 300; i++) ids.add(generator.nextLong());
        assertEquals(300, ids.size());
        long id = ids.iterator().next();
        assertEquals(3, (id >>> layout.regionShift()) & layout.regionMask());
        assertEquals(17, (id >>> layout.workerShift()) & layout.workerMask());
    }

    @Test
    void base62UsesNumericAlphabet() {
        assertEquals("0", Base62.encode(0));
        assertEquals("Z", Base62.encode(61));
        assertEquals("10", Base62.encode(62));
        assertEquals("A", Base62.encode(36));
    }

    @Test
    void waitsForNextMillisecondOnSequenceOverflow() {
        AtomicLong clock = new AtomicLong(100);
        IdLayout layout = new IdLayout(41, 1, 1, 2, 0);
        LeaseAwareIdGenerator generator = new LeaseAwareIdGenerator(layout, clock::get, ignored -> clock.incrementAndGet());
        generator.installLease(new LeaseSnapshot(1, 1, 1, EXPIRY));
        for (int i = 0; i < 4; i++) generator.nextLong();
        long next = generator.nextLong();
        assertEquals(101, next >>> (layout.regionBits() + layout.workerBits() + layout.sequenceBits()));
        assertEquals(0, next & layout.sequenceMask());
    }

    @Test
    void expiryAndExplicitFencingStopGeneration() {
        AtomicLong clock = new AtomicLong(100);
        LeaseAwareIdGenerator generator = new LeaseAwareIdGenerator(new IdLayout(41, 1, 1, 2, 0), clock::get, ignored -> { });
        generator.installLease(new LeaseSnapshot(0, 0, 1, Instant.ofEpochMilli(100)));
        assertThrows(LeaseUnavailableException.class, generator::nextLong);
        assertThrows(LeaseUnavailableException.class,
                () -> generator.installLease(new LeaseSnapshot(0, 0, 2, Instant.ofEpochMilli(200))));

        LeaseAwareIdGenerator explicitlyFenced = new LeaseAwareIdGenerator(new IdLayout(41, 1, 1, 2, 0), clock::get, ignored -> { });
        explicitlyFenced.installLease(new LeaseSnapshot(0, 0, 1, Instant.ofEpochMilli(200)));
        explicitlyFenced.fence();
        assertThrows(LeaseUnavailableException.class, explicitlyFenced::nextLong);
    }

    @Test
    void staleEpochAndClockRollbackFenceGenerator() {
        AtomicLong clock = new AtomicLong(IdLayout.DEFAULT.epochMillis() + 100);
        Instant expiry = Instant.ofEpochMilli(IdLayout.DEFAULT.epochMillis() + 2_000_000);
        LeaseAwareIdGenerator stale = new LeaseAwareIdGenerator(IdLayout.DEFAULT, clock::get, ignored -> { });
        stale.installLease(new LeaseSnapshot(1, 1, 3, expiry));
        assertThrows(LeaseUnavailableException.class, () -> stale.installLease(new LeaseSnapshot(1, 1, 2, expiry)));
        assertThrows(LeaseUnavailableException.class, stale::nextLong);

        LeaseAwareIdGenerator rollback = new LeaseAwareIdGenerator(IdLayout.DEFAULT, clock::get, ignored -> { });
        rollback.installLease(new LeaseSnapshot(1, 1, 1, expiry));
        rollback.nextLong();
        clock.set(99);
        assertThrows(ClockRollbackException.class, rollback::nextLong);
        assertThrows(LeaseUnavailableException.class, rollback::nextLong);
    }
}
