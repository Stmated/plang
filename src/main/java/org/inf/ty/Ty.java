package org.inf.ty;

import org.inf.ty.util.Tys;

import java.util.EnumSet;
import java.util.Objects;

public interface Ty {

  Ty INFER = Tys.intern(new TyNamed("INFER"));
  Ty INVALID = Tys.intern(new TyNamed("INVALID"));
  Ty UNKNOWN = Tys.intern(new TyNamed("UNKNOWN"));
  Ty DEADEND = Tys.intern(new TyNamed("DEADEND"));
  Ty VOID = Tys.intern(new TyNamed("VOID"));

  TyValueNumberInteger SHORT = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(16, false), true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger USHORT = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(16, false), false, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberInteger INTEGER = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(32, false), true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger UINTEGER = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(32, false), false, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger LONG = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(64, false), true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger ULONG = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(64, false), false, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberInteger CHAR = Tys.intern(new TyValueNumberInteger((byte) 10, new BitWidth(8, false), false, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberInteger INTEGER_BINARY = Tys.intern(new TyValueNumberInteger((byte) 2, new BitWidth(32, false), true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger INTEGER_OCTAL = Tys.intern(new TyValueNumberInteger((byte) 8, new BitWidth(32, false), true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberInteger INTEGER_HEX = Tys.intern(new TyValueNumberInteger((byte) 16, new BitWidth(32, false), true, EnumSet.noneOf(TyFlags.class)));

  TyValueNumberPrecisioned FLOAT16 = Tys.intern(new TyValueNumberPrecisioned(RealKind.FLOAT, new BitWidth(16, false), 4, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberPrecisioned FLOAT = Tys.intern(new TyValueNumberPrecisioned(RealKind.FLOAT, new BitWidth(32, false), 7, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberScaled DECIMAL = Tys.intern(new TyValueNumberScaled(new BitWidth(128, false), 10, true, EnumSet.noneOf(TyFlags.class)));
  TyValueNumberPrecisioned DOUBLE = Tys.intern(new TyValueNumberPrecisioned(RealKind.DOUBLE, new BitWidth(64, false), 16, true, EnumSet.noneOf(TyFlags.class)));
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
