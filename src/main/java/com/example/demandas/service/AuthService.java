package com.example.demandas.service;

import com.example.demandas.dto.CadastroRequest;
import com.example.demandas.dto.MeResponse;
import com.example.demandas.entity.CadastroInstitucional;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.CadastroInstitucionalRepository;
import com.example.demandas.repository.PerfilRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final CadastroInstitucionalRepository cadastroRepo;
    private final UsuarioRepository usuarioRepo;
    private final PerfilRepository perfilRepo;
    private final PasswordEncoder encoder;

    public AuthService(CadastroInstitucionalRepository cadastroRepo,
                       UsuarioRepository usuarioRepo,
                       PerfilRepository perfilRepo,
                       PasswordEncoder encoder) {
        this.cadastroRepo = cadastroRepo;
        this.usuarioRepo = usuarioRepo;
        this.perfilRepo = perfilRepo;
        this.encoder = encoder;
    }

    @Transactional
    public MeResponse cadastrar(CadastroRequest req) {
        String matricula = req.matricula().trim();
        String cpf = req.cpf().replaceAll("\\D", "");   // mantém só os dígitos
        String email = req.email().trim().toLowerCase();

        // Mensagem genérica: não revela o que existe ou não na base institucional
        CadastroInstitucional cadastro = cadastroRepo
                .findByMatriculaAndCpfAndEmailIgnoreCase(matricula, cpf, email)
                .filter(c -> "ATIVO".equals(c.getStatus()))
                .orElseThrow(() -> new RegraNegocioException(
                        "Dados não conferem com o cadastro institucional.", HttpStatus.UNPROCESSABLE_ENTITY));

        if (usuarioRepo.existsByEmailIgnoreCase(email)
                || usuarioRepo.existsByCpf(cpf)
                || usuarioRepo.existsByMatricula(matricula)) {
            throw new RegraNegocioException("Já existe um acesso para este cadastro.", HttpStatus.CONFLICT);
        }

        Perfil perfil = perfilRepo.findByNome(cadastro.getTipo())
                .orElseThrow(() -> new IllegalStateException("Perfil não encontrado: " + cadastro.getTipo()));

        Usuario u = new Usuario();
        u.setNome(cadastro.getNome());
        u.setEmail(email);
        u.setCpf(cpf);
        u.setMatricula(matricula);
        u.setSenhaHash(encoder.encode(req.senha()));
        u.getPerfis().add(perfil);
        usuarioRepo.save(u);

        return paraResposta(u);
    }

    @Transactional(readOnly = true)
    public MeResponse usuarioAtual(String email) {
        Usuario u = usuarioRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
        return paraResposta(u);
    }

    private MeResponse paraResposta(Usuario u) {
        List<String> perfis = u.getPerfis().stream().map(Perfil::getNome).sorted().toList();
        return new MeResponse(u.getId(), u.getNome(), u.getEmail(), perfis);
    }
}