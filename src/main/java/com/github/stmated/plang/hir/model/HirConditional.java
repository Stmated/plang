package com.github.stmated.plang.hir.model;

public record HirConditional(
    HirExpression predicate,
    HirExpression pass,
    HirExpression fail
) implements HirExpression {
}
