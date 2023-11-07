package com.github.stmated.plang.ty;

import java.util.EnumSet;

public interface Ty {

  Ty INFER = new TyInfer();
  Ty INVALID = new TyInvalid();
  Ty UNKNOWN = new TyUnknown();
  Ty VOID = new MyVoid();

  TyValueNumberInteger INTEGER = new TyValueNumberInteger(10, 32, true, EnumSet.noneOf(TyFlags.class));
  TyValueNumberInteger LONG = new TyValueNumberInteger(10, 64, true, EnumSet.noneOf(TyFlags.class));

  TyValueNumberInteger CHAR = new TyValueNumberInteger(10, 8, false, EnumSet.noneOf(TyFlags.class));

  TyValueNumberInteger INTEGER_BINARY = new TyValueNumberInteger(2, 32, true, EnumSet.noneOf(TyFlags.class));
  TyValueNumberInteger INTEGER_OCTAL = new TyValueNumberInteger(8, 32, true, EnumSet.noneOf(TyFlags.class));
  TyValueNumberInteger INTEGER_HEX = new TyValueNumberInteger(16, 32, true, EnumSet.noneOf(TyFlags.class));

  TyValueNumberPrecisioned FLOAT16 = new TyValueNumberPrecisioned(TyValueNumberPrecisionKind.FLOAT, 16, 4, true);
  TyValueNumberPrecisioned FLOAT = new TyValueNumberPrecisioned(TyValueNumberPrecisionKind.FLOAT, 32, 7, true);
  TyValueNumberScaled DECIMAL = new TyValueNumberScaled(128, 10, true);
  TyValueNumberPrecisioned DOUBLE = new TyValueNumberPrecisioned(TyValueNumberPrecisionKind.DOUBLE, 64, 16, true);
  TyValueNumberPrecisioned FLOAT64 = Ty.DOUBLE;

  TyValueBoolean BOOLEAN = new TyValueBoolean();

  TyValueString STRING = new TyValueString();

  default String toShortString() {
    return this.toString();
  }

  class TyInfer implements Ty {

    @Override
    public String toString() {
      return "INFER";
    }
  }

  class TyInvalid implements Ty {

    @Override
    public String toString() {
      return "INVALID";
    }
  }

  class TyUnknown implements Ty {

    @Override
    public String toString() {
      return "UNKNOWN";
    }
  }

  class MyVoid implements Ty {

    @Override
    public String toString() {
      return "VOID";
    }
  }
}
