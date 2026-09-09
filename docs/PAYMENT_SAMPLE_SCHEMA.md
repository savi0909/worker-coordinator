# Payment sample schema

The payment demonstration keeps request identity separate from payment identity:

```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    client_idempotency_key UUID NOT NULL UNIQUE,
    amount DECIMAL(19,4) NOT NULL
);
```

`client_idempotency_key` is a UUIDv7 generated or supplied by the client for request deduplication. It is not the
payment ID. `id` is the positive Snowflake-style `BIGINT` generated locally by `worker-id-client` from the currently
valid coordinator lease. Base62 is only an optional transport representation of that numeric payment ID; it is not
stored in this table.

The executable sample provisions this table through Flyway and persists payments through Spring Data JPA. The
coordinator remains generic and does not know about this schema.
