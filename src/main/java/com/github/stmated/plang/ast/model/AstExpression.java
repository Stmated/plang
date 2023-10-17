package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public interface AstExpression {

  <R, V extends AstVisitor<R>> R visit(V visitor);
}
