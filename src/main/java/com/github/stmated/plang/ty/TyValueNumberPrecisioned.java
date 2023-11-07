package com.github.stmated.plang.ty;

public record TyValueNumberPrecisioned(TyValueNumberPrecisionKind kind, int width, int precision, boolean signed) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {

    return switch (kind) {
//      case DECIMAL -> TyValueKind.DECIMAL;
      case DOUBLE -> TyValueKind.DOUBLE;
      case FLOAT -> TyValueKind.FLOAT;
    };
  }

  @Override
  public int radix() {
    return 10;
  }
}
