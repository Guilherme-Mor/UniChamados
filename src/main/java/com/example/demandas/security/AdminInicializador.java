package com.example.demandas.security;

import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.Usuario;
import com.example.demandas.repository.PerfilRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminInicializador implements ApplicationRunner {

    private final UsuarioRepository usuarioRepo;
    private final PerfilRepository perfilRepo;
    private final PasswordEncoder encoder;
    private final String emailAdmin;
    private final String senhaAdmin;

    public AdminInicializador(UsuarioRepository usuarioRepo,
                              PerfilRepository perfilRepo,
                              PasswordEncoder encoder,
                              @Value("${app.admin.email}") String emailAdmin,
                              @Value("${app.admin.senha}") String senhaAdmin) {
        this.usuarioRepo = usuarioRepo;
        this.perfilRepo = perfilRepo;
        this.encoder = encoder;
        this.emailAdmin = emailAdmin.trim().toLowerCase();
        this.senhaAdmin = senhaAdmin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepo.existsByEmailIgnoreCase(emailAdmin)) return;

        Perfil perfilAdmin = perfilRepo.findByNome("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Perfil ADMIN não existe"));

        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail(emailAdmin);
        admin.setCpf("00000000000");
        admin.setMatricula("ADMIN");
        admin.setSenhaHash(encoder.encode(senhaAdmin));
        admin.getPerfis().add(perfilAdmin);
        usuarioRepo.save(admin);
    }
}