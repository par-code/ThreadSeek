-- Optional: ThreadSeek creates this automatically on first use.
-- Run it manually only if you prefer to set the database up yourself.
CREATE DATABASE IF NOT EXISTS threadseek;
USE threadseek;

CREATE TABLE IF NOT EXISTS search_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    search_name VARCHAR(255),
    extension VARCHAR(20),
    location VARCHAR(500),
    total_found INT,
    time_taken_seconds DOUBLE,
    search_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
