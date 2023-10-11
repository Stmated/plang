package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public interface InitialExpression {

  <R, V extends InitialVisitor<R>> R visit(V visitor);
}
