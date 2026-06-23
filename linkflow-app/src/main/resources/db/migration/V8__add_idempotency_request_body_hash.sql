ALTER TABLE idempotency_records ADD COLUMN request_body_hash VARCHAR(64) NOT NULL DEFAULT '';

-- Remove default after backfill so new records must supply a hash
ALTER TABLE idempotency_records ALTER COLUMN request_body_hash DROP DEFAULT;
