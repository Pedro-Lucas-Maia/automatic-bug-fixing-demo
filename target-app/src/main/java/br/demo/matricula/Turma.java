package br.demo.matricula;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class Turma {
    private UUID turmaId;
    private String codigo;

    public Turma(String codigo) {
        this.turmaId = UUID.randomUUID();
        this.codigo = codigo;
    }
}
