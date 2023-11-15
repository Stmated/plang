package com.github.stmated.plang.ty;

import com.github.stmated.plang.ty.util.Tys;
import java.util.EnumSet;
import java.util.Objects;

public interface Ty {

  Ty INFER = Tys.intern(new TyNamed("INFER"));
  Ty INVALID = Tys.intern(new TyNamed("INVALID"));
  Ty UNKNOWN = Tys.intern(new TyNamed("UNKNOWN"));
  Ty DEADEND = Tys.intern(new TyNamed("DEADEND"));
  Ty VOID = Tys.intern(new TyNamed("VOID"));

  TyValueNumberInteger SHORT = Tys.intern(new TyValueNumberInteger((byte) 10, 16, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger USHORT = Tys.intern(new TyValueNumberInteger((byte) 10, 16, false, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberInteger INTEGER = Tys.intern(new TyValueNumberInteger((byte) 10, 32, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger UINTEGER = Tys.intern(new TyValueNumberInteger((byte) 10, 32, false, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger LONG = Tys.intern(new TyValueNumberInteger((byte) 10, 64, true, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberInteger CHAR = Tys.intern(new TyValueNumberInteger((byte) 10, 8, false, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberInteger INTEGER_BINARY = Tys.intern(new TyValueNumberInteger((byte) 2, 32, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger INTEGER_OCTAL = Tys.intern(new TyValueNumberInteger((byte) 8, 32, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger INTEGER_HEX = Tys.intern(new TyValueNumberInteger((byte) 16, 32, true, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberPrecisioned FLOAT16 = Tys.intern(new TyValueNumberPrecisioned(RealKind.FLOAT, 16, 4, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberPrecisioned FLOAT = Tys.intern(new TyValueNumberPrecisioned(RealKind.FLOAT, 32, 7, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberScaled DECIMAL = Tys.intern(new TyValueNumberScaled(128, 10, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberPrecisioned DOUBLE = Tys.intern(new TyValueNumberPrecisioned(RealKind.DOUBLE, 64, 16, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberPrecisioned FLOAT64 = Tys.intern(Ty.DOUBLE);

  TyValueBoolean BOOLEAN = Tys.intern(new TyValueBoolean());

  TyValueString STRING = Tys.intern(new TyValueString());

  default Ty intern() {
    return Tys.intern(this);
  }

  default String toShortString() {
    return this.toString();
  }

  record TyNamed(String name) implements Ty {

    @Override
    public String toString() {
      return this.name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final var that = (TyNamed) o;
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }
}
