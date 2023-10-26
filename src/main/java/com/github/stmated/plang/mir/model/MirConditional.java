package com.github.stmated.plang.mir.model;

public record MirConditional(MirExpression predicate, MirBlock pass, MirBlock fail, MirBlock merge) implements MirExpression {

}
