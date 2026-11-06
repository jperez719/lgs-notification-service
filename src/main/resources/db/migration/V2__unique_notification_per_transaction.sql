-- Keep the earliest notification per transaction, drop the rest
DELETE FROM notifications n
    USING notifications d
WHERE n.transaction_id = d.transaction_id
  AND (n.created_at, n.id) > (d.created_at, d.id);

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_transaction_id UNIQUE (transaction_id);