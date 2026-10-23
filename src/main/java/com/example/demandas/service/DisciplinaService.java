package com.example.demandas.service;

import com.example.demandas.dto.DisciplinaRequest;
import com.example.demandas.dto.DisciplinaResponse;
import com.example.demandas.entity.Disciplina;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.DisciplinaRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepo;
    private final UsuarioRepository usuarioRepo;

    public DisciplinaService(DisciplinaRepository disciplinaRepo, UsuarioRepository usuarioRepo) {
        this.disciplinaRepo = disciplinaRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional(readOnly = true)
    public List<DisciplinaResponse> listar() {
        return disciplinaRepo.findByAtivoTrueOrderByNomeAsc().stream().map(this::paraResposta).toList();
    }

    @Transactional
    public DisciplinaResponse criar(DisciplinaRequest req) {
        String codigo = req.codigo().trim().toUpperCase();
        if (disciplinaRepo.existsByCodigoIgnoreCase(codigo)) {
            throw new RegraNegocioException("Já existe uma disciplina com esse código.", HttpStatus.CONFLICT);
        }
        Disciplina d = new Disciplina();
        d.setCodigo(codigo);
        d.setNome(req.nome().trim());
        disciplinaRepo.save(d);
        return paraResposta(d);
    }

    @Transactional
    public void definirDocente(Integer disciplinaId, Integer docenteId) {
        Disciplina d = disciplinaRepo.findById(disciplinaId)
                .orElseThrow(() -> new RegraNegocioException("Disciplina não encontrada.", HttpStatus.NOT_FOUND));

        if (docenteId == null) {
            d.setDocente(null);
            return;
        }
        Usuario docente = usuarioRepo.findById(docenteId)
                .filter(u -> "ATIVO".equals(u.getStatus()))
                .filter(u -> u.getPerfis().stream().anyMatch(p -> "DOCENTE".equals(p.getNome())))
                .orElseThrow(() -> new RegraNegocioException(
                        "Responsável deve ser um docente válido.", HttpStatus.UNPROCESSABLE_ENTITY));
        d.setDocente(docente);
    }

    private DisciplinaResponse paraResposta(Disciplina d) {
        Usuario doc = d.getDocente();
        return new DisciplinaResponse(d.getId(), d.getCodigo(), d.getNome(),
                doc == null ? null : doc.getId(), doc == null ? null : doc.getNome());
    }
}