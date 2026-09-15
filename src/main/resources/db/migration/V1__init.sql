CREATE TABLE notifications (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               tenant_id UUID NOT NULL,
                               customer_id UUID NOT NULL,
                               transaction_id UUID NOT NULL,
                               message TEXT NOT NULL,
                               status VARCHAR(20) NOT NULL,
                               created_at TIMESTAMP NOT NULL DEFAULT now()
);