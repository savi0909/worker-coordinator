package com.example.workerclient;

import com.example.workeridclient.IdLayout;
import com.example.workeridclient.LeaseAwareIdGenerator;
import com.example.workeridclient.LeaseSnapshot;
import org.springframework.stereotype.Component;

/** Payment-specific demonstration boundary; the reusable generator remains domain-neutral. */
@Component
public final class PaymentIdGenerator {
    private final LeaseAwareIdGenerator generator;

    public PaymentIdGenerator(WorkerClientProperties properties) {
        this.generator = new LeaseAwareIdGenerator(new IdLayout(properties.timestampBits(), properties.regionBits(),
                properties.workerBits(), properties.sequenceBits(), properties.epochMillis()));
    }

    public void leaseAcquired(CoordinatorClient.Lease lease) {
        generator.installLease(new LeaseSnapshot(lease.regionId(), lease.workerId(), lease.epoch(), lease.leaseExpiry()));
    }

    public void leaseLost() { generator.fence(); }
    public long nextPaymentIdLong() { return generator.nextLong(); }
    public String nextPaymentId() { return generator.nextBase62(); }
}
