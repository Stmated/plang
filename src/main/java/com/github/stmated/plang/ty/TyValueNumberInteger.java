package com.github.stmated.plang.ty;

import java.util.EnumSet;

public record TyValueNumberInteger(int radix, int width, boolean signed, EnumSet<TyFlags> flags) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.INTEGER;
  }

  @Override
  public String toShortString() {
    return STR."\{signed ? "" : "u"}int\{width}\{radix == 10 ? "" : STR."base\{radix}"}";
  }

  @Override
  public boolean isConstant() {
    return flags.contains(TyFlags.CONSTANT);
  }

  @Override
  public boolean isImmutable() {
    return flags.contains(TyFlags.IMMUTABLE) || flags.contains(TyFlags.CONSTANT);
  }
}
