package com.github.stmated.plang.ty;

public record TyValueNumberScaled(int width, int scale, boolean signed) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.DECIMAL;
  }

  @Override
  public byte radix() {
    return 10;
  }
}
