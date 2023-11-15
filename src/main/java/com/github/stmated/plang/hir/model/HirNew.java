package com.github.stmated.plang.hir.model;

public record HirNew(HirType type, HirArgument[] arguments) implements HirExpression {


}
