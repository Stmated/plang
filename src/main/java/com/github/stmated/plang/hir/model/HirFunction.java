package com.github.stmated.plang.hir.model;

public record HirFunction(
  HirFunctionSignature signature,
  HirExpression body
) implements HirExpression {

  @Override
  public String toString() {
    return STR."\{signature} => \{body}";
  }
}
