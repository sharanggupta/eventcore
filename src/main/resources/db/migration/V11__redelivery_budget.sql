-- Redelivery grants a fresh retry cycle: a delivery gives up when attempts
-- reach this budget, and requeuing raises the budget by max-attempts again.
ALTER TABLE webhook_deliveries ADD COLUMN gives_up_after INT NOT NULL DEFAULT 5;
