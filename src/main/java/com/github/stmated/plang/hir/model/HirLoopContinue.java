package com.github.stmated.plang.hir.model;

/**
 * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
 *        Are there benefits to being able to represent a "continue" further down the chain?
 */
public record HirLoopContinue() implements HirExpression {

}
