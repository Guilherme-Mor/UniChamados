-- V4 - Registra qual admin revogou o vínculo do Telegram
ALTER TABLE telegram_vinculo ADD COLUMN revogado_por_id INTEGER;
ALTER TABLE telegram_vinculo ADD CONSTRAINT fk_tgv_revogado_por
    FOREIGN KEY (revogado_por_id) REFERENCES usuario(id);