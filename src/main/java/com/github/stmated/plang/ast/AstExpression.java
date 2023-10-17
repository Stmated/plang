package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public interface AstExpression {

  <R, V extends AstVisitor<R>> R visit(V visitor);
}
