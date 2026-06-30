ALTER TABLE refresh_token
    ADD INDEX idx_refresh_token_token (refresh_token(255));
