INSERT IGNORE INTO customers (id, full_name, email, created_at) VALUES
    (1, 'Alice Johnson', 'alice@example.com', CURRENT_TIMESTAMP),
    (2, 'Bob Smith', 'bob@example.com', CURRENT_TIMESTAMP),
    (3, 'Charlie Brown', 'charlie@example.com', CURRENT_TIMESTAMP);
