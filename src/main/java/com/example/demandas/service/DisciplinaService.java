package com.example.demandas.service;

import com.example.demandas.dto.DisciplinaEdicaoRequest;
import com.example.demandas.dto.DisciplinaRequest;
import com.example.demandas.dto.DisciplinaResponse;
import com.example.demandas.entity.Disciplina;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.ChamadoRepository;
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
    private final ChamadoRepository chamadoRepo;

    public DisciplinaService(DisciplinaRepository disciplinaRepo,
                             UsuarioRepository usuarioRepo,
                             ChamadoRepository chamadoRepo) {
        this.disciplinaRepo = disciplinaRepo;
        this.usuarioRepo = usuarioRepo;
        this.chamadoRepo = chamadoRepo;
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

    /** Edita código, nome e docente na mesma transação. */
    @Transactional
    public DisciplinaResponse atualizar(Integer id, DisciplinaEdicaoRequest req) {
        Disciplina d = buscarAtiva(id);
        String codigo = req.codigo().trim().toUpperCase();
        if (disciplinaRepo.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new RegraNegocioException("Já existe outra disciplina com esse código.", HttpStatus.CONFLICT);
        }
        Usuario docente = resolverDocente(req.docenteId());
        d.setCodigo(codigo);
        d.setNome(req.nome().trim());
        d.setDocente(docente);
        return paraResposta(d);
    }

    @Transactional
    public void definirDocente(Integer disciplinaId, Integer docenteId) {
        Disciplina d = buscarAtiva(disciplinaId);
        d.setDocente(resolverDocente(docenteId));
    }

    /** Exclui se não houver chamados; caso contrário inativa, preservando o histórico. */
    @Transactional
    public String remover(Integer id) {
        Disciplina d = buscarAtiva(id);
        if (chamadoRepo.existsByDisciplinaId(id)) {
            d.setAtivo(false);
            return "Disciplina inativada: há chamados vinculados e o histórico foi preservado.";
        }
        disciplinaRepo.delete(d);
        return "Disciplina excluída.";
    }

    private Disciplina buscarAtiva(Integer id) {
        return disciplinaRepo.findById(id)
                .filter(d -> Boolean.TRUE.equals(d.getAtivo()))
                .orElseThrow(() -> new RegraNegocioException("Disciplina não encontrada.", HttpStatus.NOT_FOUND));
    }

    private Usuario resolverDocente(Integer docenteId) {
        if (docenteId == null) return null;
        return usuarioRepo.findById(docenteId)
                .filter(u -> "ATIVO".equals(u.getStatus()))
                .filter(u -> u.getPerfis().stream().anyMatch(p -> "DOCENTE".equals(p.getNome())))
                .orElseThrow(() -> new RegraNegocioException(
                        "Responsável deve ser um docente válido.", HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private DisciplinaResponse paraResposta(Disciplina d) {
        Usuario doc = d.getDocente();
        return new DisciplinaResponse(d.getId(), d.getCodigo(), d.getNome(),
                doc == null ? null : doc.getId(), doc == null ? null : doc.getNome());
    }
}