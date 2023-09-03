package com.github.stmated.plang.hir;

public record Conditional(
    Expression predicate,
    Expression pass,
    Expression fail
) {
}
