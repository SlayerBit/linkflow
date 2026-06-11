-- Align url_analytics with AuditableEntity (created_by, updated_by)
ALTER TABLE url_analytics ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE url_analytics ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
