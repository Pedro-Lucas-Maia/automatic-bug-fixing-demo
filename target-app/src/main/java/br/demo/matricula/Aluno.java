package br.demo.matricula;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class Aluno {
    private UUID id;
    private String nome;
    private String matricula;
    private String curso;
    private Turma turma;

    public Aluno(String nome, String matricula, String curso) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.matricula = matricula;
        this.curso = curso;
    }
}
