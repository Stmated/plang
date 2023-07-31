package com.github.stmated.plang.hir;

public record Block(Expression[] children) implements Expression {
}
