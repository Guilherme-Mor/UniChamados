package com.example.demandas.service;

import com.example.demandas.dto.CadastroInstitucionalRequest;
import com.example.demandas.dto.CadastroInstitucionalResponse;
import com.example.demandas.dto.UsuarioAdminResponse;
import com.example.demandas.entity.CadastroInstitucional;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.CadastroInstitucionalRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UsuarioRepository usuarioRepo;
    private final CadastroInstitucionalRepository cadastroRepo;

    public AdminService(UsuarioRepository usuarioRepo, CadastroInstitucionalRepository cadastroRepo) {
        this.usuarioRepo = usuarioRepo;
        this.cadastroRepo = cadastroRepo;
    }

    // ===== Usuários =====

    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> listarUsuarios() {
        return usuarioRepo.findAll(Sort.by("nome")).stream().map(this::paraResposta).toList();
    }

    @Transactional
    public UsuarioAdminResponse alterarStatus(String emailLogado, Integer id, boolean ativo) {
        Usuario u = usuarioRepo.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
        if (!ativo && u.getEmail().equalsIgnoreCase(emailLogado)) {
            throw new RegraNegocioException("Você não pode desativar o próprio acesso.", HttpStatus.CONFLICT);
        }
        u.setStatus(ativo ? "ATIVO" : "INATIVO");
        return paraResposta(u);
    }

    private UsuarioAdminResponse paraResposta(Usuario u) {
        List<String> perfis = u.getPerfis().stream().map(Perfil::getNome).sorted().toList();
        return new UsuarioAdminResponse(u.getId(), u.getNome(), u.getEmail(), u.getMatricula(), perfis, u.getStatus());
    }

    // ===== Cadastro institucional =====

    @Transactional(readOnly = true)
    public List<CadastroInstitucionalResponse> listarCadastros() {
        return cadastroRepo.findAll(Sort.by("nome")).stream()
                .map(c -> paraResposta(c, usuarioRepo.existsByMatricula(c.getMatricula())))
                .toList();
    }

    @Transactional
    public CadastroInstitucionalResponse criarCadastro(CadastroInstitucionalRequest req) {
        String matricula = req.matricula().trim();
        String cpf = req.cpf().replaceAll("\\D", "");
        String email = req.email().trim().toLowerCase();
        String tipo = req.tipo().trim().toUpperCase();

        if (cpf.length() != 11) {
            throw new RegraNegocioException("CPF deve ter 11 dígitos.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!tipo.equals("DISCENTE") && !tipo.equals("DOCENTE")) {
            throw new RegraNegocioException("Tipo deve ser DISCENTE ou DOCENTE.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (cadastroRepo.existsByMatricula(matricula)
                || cadastroRepo.existsByCpf(cpf)
                || cadastroRepo.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException(
                    "Já existe um cadastro com essa matrícula, CPF ou e-mail.", HttpStatus.CONFLICT);
        }

        CadastroInstitucional c = new CadastroInstitucional();
        c.setMatricula(matricula);
        c.setCpf(cpf);
        c.setNome(req.nome().trim());
        c.setEmail(email);
        c.setTipo(tipo);
        c.setStatus("ATIVO");
        cadastroRepo.save(c);
        return paraResposta(c, false);
    }

    private CadastroInstitucionalResponse paraResposta(CadastroInstitucional c, boolean acessoCriado) {
        String cpf = c.getCpf();
        String mascarado = "***.***.***-" + cpf.substring(cpf.length() - 2);
        return new CadastroInstitucionalResponse(c.getId(), c.getMatricula(), mascarado, c.getNome(),
                c.getEmail(), c.getTipo(), c.getStatus(), acessoCriado);
    }
}