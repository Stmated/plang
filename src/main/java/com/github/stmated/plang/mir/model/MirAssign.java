package com.github.stmated.plang.mir.model;

public record MirAssign(MirExpression target, MirExpression value) implements MirExpression {

}
