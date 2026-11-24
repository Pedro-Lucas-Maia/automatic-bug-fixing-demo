package br.demo.matricula;

import java.util.Optional;

public interface AlunoRepository {

    Optional<Aluno> findByMatricula(String matricula);

    Aluno save(Aluno aluno);
}
