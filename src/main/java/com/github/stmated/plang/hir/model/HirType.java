package com.github.stmated.plang.hir.model;

public record HirType(HirIdentifier identifier) {

  public static final HirType TYPE_UNKNOWN = new HirType(new HirIdentifier("<unknown>"));
  public static final HirType TYPE_ANY = new HirType(new HirIdentifier("<any>"));
  public static final HirType TYPE_EMPTY = new HirType(new HirIdentifier("<empty>"));

  public static HirType getCommonDenominator(HirType a, HirType b) {

    // TODO: This needs implementation :)
    return a;
  }

  public static HirType asArray(final HirType type, long expectedSize) {

    // TODO: This needs implementation :)
    return null;
  }

  public static long getAssumedLengthBetween(final HirType commonType, final HirType a, final HirType b) {

    // TODO: This needs implementation :)
    return -1;
  }

  public static HirType fromJavaType(final Object literal) {

    // TODO: This needs implementation :)
    return null;
  }

  public static HirType getInvertedType(final HirType resultType) {

    // TODO: This needs implementation :)
    // Should be the opposite of the incoming. so true => false
    return resultType;
  }
}
