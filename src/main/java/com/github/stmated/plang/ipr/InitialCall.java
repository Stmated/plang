package com.github.stmated.plang.ipr;

public record InitialCall(
    InitialExpression target,
    InitialParen paren,
    boolean onErrorBubbleUp,
    boolean partial
) implements InitialExpression {
}
