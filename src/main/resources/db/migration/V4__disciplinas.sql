CREATE TABLE disciplina (
                            id         INT IDENTITY(1,1) PRIMARY KEY,
                            codigo     VARCHAR(20)   NOT NULL UNIQUE,
                            nome       NVARCHAR(150) NOT NULL,
                            docente_id INT NULL,
                            ativo      BIT NOT NULL DEFAULT 1,
                            CONSTRAINT fk_disciplina_docente FOREIGN KEY (docente_id) REFERENCES usuario(id)
);
GO

ALTER TABLE categoria
    ADD exige_disciplina BIT NOT NULL CONSTRAINT df_categoria_exige_disc DEFAULT 0;

ALTER TABLE chamado
    ADD disciplina_id INT NULL CONSTRAINT fk_chamado_disciplina REFERENCES disciplina(id);
GO

-- Nota e Frequência (e suas subcategorias) passam a exigir disciplina
UPDATE categoria SET exige_disciplina = 1
WHERE nome IN (N'Nota', N'Frequência')
   OR categoria_pai_id IN (SELECT id FROM categoria
                           WHERE nome IN (N'Nota', N'Frequência') AND categoria_pai_id IS NULL);

-- Disciplinas de exemplo (o docente é definido pelo admin na tela)
INSERT INTO disciplina (codigo, nome) VALUES
                                          ('MAT01', N'Matemática'),
                                          ('POR01', N'Português'),
                                          ('FIS01', N'Física');