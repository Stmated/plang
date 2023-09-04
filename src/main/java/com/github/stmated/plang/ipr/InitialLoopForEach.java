package com.github.stmated.plang.ipr;

/**
 *
 * @param source
 * @param target Can be null, then the variable declaration is implicit and the variable will be called 'it'
 * @param body
 */
public record InitialLoopForEach(
    InitialExpression source,
    InitialExpression target,
    InitialExpression body
) implements InitialExpression {
}
