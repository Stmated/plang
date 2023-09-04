package com.github.stmated.plang.ipr;

public record InitialCall(
    InitialExpression target,
    InitialParen paren,
    InitialExpression[] generics,
    boolean onErrorBubbleUp,
    boolean partial
) implements InitialExpression {
}
