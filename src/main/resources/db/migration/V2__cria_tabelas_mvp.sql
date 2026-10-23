-- Remove a tabela de teste da V1
DROP TABLE teste_conexao;

-- ===== Perfis e status =====
CREATE TABLE perfil (
                        id   INT IDENTITY(1,1) PRIMARY KEY,
                        nome VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE status_chamado (
                                id        INT IDENTITY(1,1) PRIMARY KEY,
                                nome      VARCHAR(30)  NOT NULL UNIQUE,
                                descricao NVARCHAR(200) NULL,
                                ordem     INT NOT NULL
);

-- ===== Cadastro institucional (base de referência) =====
CREATE TABLE cadastro_institucional (
                                        id        INT IDENTITY(1,1) PRIMARY KEY,
                                        matricula VARCHAR(20)   NOT NULL UNIQUE,
                                        cpf       VARCHAR(11)   NOT NULL UNIQUE,
                                        nome      NVARCHAR(150) NOT NULL,
                                        email     VARCHAR(150)  NOT NULL UNIQUE,
                                        tipo      VARCHAR(20)   NOT NULL,   -- DISCENTE ou DOCENTE
                                        status    VARCHAR(20)   NOT NULL DEFAULT 'ATIVO'
);

-- ===== Usuários =====
CREATE TABLE usuario (
                         id         INT IDENTITY(1,1) PRIMARY KEY,
                         nome       NVARCHAR(150) NOT NULL,
                         email      VARCHAR(150)  NOT NULL UNIQUE,
                         cpf        VARCHAR(11)   NOT NULL UNIQUE,
                         matricula  VARCHAR(20)   NOT NULL UNIQUE,
                         senha_hash VARCHAR(100)  NOT NULL,
                         status     VARCHAR(20)   NOT NULL DEFAULT 'ATIVO',
                         criado_em  DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE usuario_perfil (
                                usuario_id INT NOT NULL,
                                perfil_id  INT NOT NULL,
                                PRIMARY KEY (usuario_id, perfil_id),
                                CONSTRAINT fk_up_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
                                CONSTRAINT fk_up_perfil  FOREIGN KEY (perfil_id)  REFERENCES perfil(id)
);

-- ===== Categorias (árvore) =====
CREATE TABLE categoria (
                           id               INT IDENTITY(1,1) PRIMARY KEY,
                           nome             NVARCHAR(100) NOT NULL,
                           descricao        NVARCHAR(300) NULL,
                           categoria_pai_id INT NULL,
                           prazo_horas      INT NULL,          -- usado no SLA (fase futura)
                           ativo            BIT NOT NULL DEFAULT 1,
                           ordem            INT NOT NULL DEFAULT 0,
                           CONSTRAINT fk_categoria_pai FOREIGN KEY (categoria_pai_id) REFERENCES categoria(id)
);

-- ===== Chamados =====
CREATE SEQUENCE seq_protocolo START WITH 1 INCREMENT BY 1;

CREATE TABLE chamado (
                         id             BIGINT IDENTITY(1,1) PRIMARY KEY,
                         protocolo      VARCHAR(20)   NOT NULL UNIQUE,   -- AAAAMMDD-NNNNNN
                         usuario_id     INT           NOT NULL,
                         categoria_id   INT           NOT NULL,
                         status_id      INT           NOT NULL,
                         responsavel_id INT           NULL,
                         titulo         NVARCHAR(150) NOT NULL,
                         descricao      NVARCHAR(MAX) NOT NULL,
                         prioridade     VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
                         criado_em      DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
                         atualizado_em  DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
                         encerrado_em   DATETIME2     NULL,
                         CONSTRAINT fk_chamado_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuario(id),
                         CONSTRAINT fk_chamado_categoria   FOREIGN KEY (categoria_id)   REFERENCES categoria(id),
                         CONSTRAINT fk_chamado_status      FOREIGN KEY (status_id)      REFERENCES status_chamado(id),
                         CONSTRAINT fk_chamado_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuario(id)
);

CREATE INDEX ix_chamado_usuario     ON chamado(usuario_id);
CREATE INDEX ix_chamado_responsavel ON chamado(responsavel_id);
CREATE INDEX ix_chamado_status      ON chamado(status_id);

-- ===== Histórico =====
CREATE TABLE interacao (
                           id         BIGINT IDENTITY(1,1) PRIMARY KEY,
                           chamado_id BIGINT        NOT NULL,
                           usuario_id INT           NOT NULL,
                           canal      VARCHAR(20)   NOT NULL DEFAULT 'PORTAL',   -- TELEGRAM entra depois
                           tipo       VARCHAR(30)   NOT NULL,   -- MENSAGEM, MUDANCA_STATUS, ATRIBUICAO
                           mensagem   NVARCHAR(MAX) NULL,
                           criado_em  DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
                           CONSTRAINT fk_interacao_chamado FOREIGN KEY (chamado_id) REFERENCES chamado(id),
                           CONSTRAINT fk_interacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE INDEX ix_interacao_chamado ON interacao(chamado_id);

-- ===== Dados iniciais =====
INSERT INTO perfil (nome) VALUES ('DISCENTE'), ('DOCENTE'), ('ADMIN');

INSERT INTO status_chamado (nome, descricao, ordem) VALUES
                                                        ('ABERTO',              N'Chamado criado pelo discente', 1),
                                                        ('ATRIBUIDO',           N'Responsável definido', 2),
                                                        ('EM_ATENDIMENTO',      N'Responsável iniciou a tratativa', 3),
                                                        ('AGUARDANDO_DISCENTE', N'Depende de informação do discente', 4),
                                                        ('RESOLVIDO',           N'Problema tratado', 5),
                                                        ('ENCERRADO',           N'Atendimento finalizado', 6),
                                                        ('CANCELADO',           N'Chamado cancelado com justificativa', 7);

-- Categorias raiz
INSERT INTO categoria (nome, descricao, prazo_horas, ordem) VALUES
                                                                (N'Nota',       N'Demandas sobre notas', 72, 1),
                                                                (N'Frequência', N'Demandas sobre faltas e presença', 72, 2),
                                                                (N'Documentos', N'Declarações e histórico', 120, 3),
                                                                (N'Outros',     N'Demandas não contempladas', 120, 4);

-- Subcategorias
INSERT INTO categoria (nome, categoria_pai_id, prazo_horas, ordem)
SELECT N'Revisão de nota', id, 72, 1 FROM categoria WHERE nome = N'Nota' AND categoria_pai_id IS NULL;
INSERT INTO categoria (nome, categoria_pai_id, prazo_horas, ordem)
SELECT N'Nota não lançada', id, 48, 2 FROM categoria WHERE nome = N'Nota' AND categoria_pai_id IS NULL;
INSERT INTO categoria (nome, categoria_pai_id, prazo_horas, ordem)
SELECT N'Justificativa de falta', id, 72, 1 FROM categoria WHERE nome = N'Frequência' AND categoria_pai_id IS NULL;
INSERT INTO categoria (nome, categoria_pai_id, prazo_horas, ordem)
SELECT N'Revisão de frequência', id, 72, 2 FROM categoria WHERE nome = N'Frequência' AND categoria_pai_id IS NULL;
INSERT INTO categoria (nome, categoria_pai_id, prazo_horas, ordem)
SELECT N'Declaração de vínculo', id, 120, 1 FROM categoria WHERE nome = N'Documentos' AND categoria_pai_id IS NULL;
INSERT INTO categoria (nome, categoria_pai_id, prazo_horas, ordem)
SELECT N'Histórico escolar', id, 120, 2 FROM categoria WHERE nome = N'Documentos' AND categoria_pai_id IS NULL;

-- Cadastros institucionais de teste (CPFs fictícios)
INSERT INTO cadastro_institucional (matricula, cpf, nome, email, tipo) VALUES
                                                                           ('2026001', '11111111111', N'Aluno Teste Um',     'aluno1@teste.local',   'DISCENTE'),
                                                                           ('2026002', '22222222222', N'Aluno Teste Dois',   'aluno2@teste.local',   'DISCENTE'),
                                                                           ('D001',    '33333333333', N'Docente Teste Um',   'docente1@teste.local', 'DOCENTE'),
                                                                           ('D002',    '44444444444', N'Docente Teste Dois', 'docente2@teste.local', 'DOCENTE');