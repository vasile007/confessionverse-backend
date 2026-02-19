ALTER TABLE subscription
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS current_period_start DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS current_period_end DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS cancel_at_period_end BIT NULL,
    ADD COLUMN IF NOT EXISTS last_payment_at DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS last_invoice_id VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS created_at DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME(6) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_stripe_subscription_id ON subscription (stripe_subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_stripe_customer_id ON subscription (stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_subscription_user_id_updated_at ON subscription (user_id, updated_at);

CREATE TABLE IF NOT EXISTS processed_stripe_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_processed_stripe_event_id UNIQUE (event_id)
);
