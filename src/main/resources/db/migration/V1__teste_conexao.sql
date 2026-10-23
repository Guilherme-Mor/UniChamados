CREATE TABLE teste_conexao (
                               id INT IDENTITY(1,1) PRIMARY KEY,
                               criado_em DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);