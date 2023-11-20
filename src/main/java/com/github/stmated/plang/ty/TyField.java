package com.github.stmated.plang.ty;

public record TyField(String name, Ty ty) {

  @Override
  public String toString() {
    return STR."\{name}: \{ty}";
  }
}
