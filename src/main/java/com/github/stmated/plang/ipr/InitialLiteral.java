package com.github.stmated.plang.ipr;

/**
 * TODO: Probably not good, since it relies on the data types of Java and not the actual target language
 */
public record InitialLiteral(
    Object value
) implements InitialExpression {
}
