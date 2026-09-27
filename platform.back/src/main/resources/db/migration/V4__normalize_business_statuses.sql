-- Run once after V3 in Navicat, against the ceramic database.
UPDATE orders SET status = CASE LOWER(status)
    WHEN 'paid' THEN 'PAID'
    WHEN 'shipped' THEN 'SHIPPED'
    WHEN 'delivered' THEN 'COMPLETED'
    WHEN 'cancelled' THEN 'CANCELLED'
    ELSE status END;

UPDATE custom_orders SET status = CASE LOWER(status)
    WHEN 'pending' THEN 'PENDING'
    WHEN 'approved' THEN 'QUOTED'
    WHEN 'in_progress' THEN 'IN_PROGRESS'
    WHEN 'completed' THEN 'COMPLETED'
    WHEN 'rejected' THEN 'REJECTED'
    WHEN 'cancelled' THEN 'CANCELLED'
    ELSE status END;
