package com.github.stmated.plang.ty;

public record TyValueNumberPrecisioned(TyValueNumberPrecisionKind kind, int width, int precision, boolean signed) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {

    return switch (kind) {
      case DOUBLE -> TyValueKind.DOUBLE;
      case FLOAT -> TyValueKind.FLOAT;
    };
  }

  @Override
  public byte radix() {
    return 10;
  }
}
