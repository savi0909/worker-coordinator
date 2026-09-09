CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    client_idempotency_key UUID NOT NULL UNIQUE,
    amount DECIMAL(19,4) NOT NULL
);
