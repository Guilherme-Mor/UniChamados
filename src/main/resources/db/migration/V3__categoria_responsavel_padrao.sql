ALTER TABLE categoria
    ADD responsavel_padrao_id INT NULL
    CONSTRAINT fk_categoria_resp_padrao REFERENCES usuario(id);