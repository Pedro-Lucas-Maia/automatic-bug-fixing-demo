package br.demo.matricula;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatriculaService {

    private final AlunoRepository alunoRepository;

    public String confirmarMatricula(Aluno aluno) {
        if (aluno == null) {
            throw new IllegalArgumentException("Aluno não pode ser nulo");
        }

        String codigoTurma = aluno.getTurma().getCodigo();

        alunoRepository.save(aluno);
        return "Matrícula " + aluno.getMatricula() + " confirmada na turma " + codigoTurma;
    }

    public String confirmarMatriculaPorNumero(String numeroMatricula) {
        Aluno aluno = alunoRepository.findByMatricula(numeroMatricula)
                .orElseThrow(() -> new IllegalArgumentException("Aluno não encontrado: " + numeroMatricula));

        return confirmarMatricula(aluno);
    }
}
