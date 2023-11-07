package com.github.stmated.plang.ty;

public record TyValueString() implements TyValue {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.STRING;
  }

  @Override
  public String toString() {
    return "String";
  }
}
