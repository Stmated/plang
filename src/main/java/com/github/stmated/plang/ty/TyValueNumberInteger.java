package com.github.stmated.plang.ty;

import java.util.EnumSet;
import java.util.Objects;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

@Builder(toBuilder = true)
public record TyValueNumberInteger(byte radix, BitWidth width, boolean signed, EnumSet<TyFlags> flags) implements TyValueNumber {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.INTEGER;
  }

  @Override
  public String toString() {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TyValueNumberInteger that = (TyValueNumberInteger) o;
    return radix == that.radix && Objects.equals(width, that.width) && signed == that.signed && Objects.equals(flags, that.flags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(radix, width, signed, flags);
  }
}
