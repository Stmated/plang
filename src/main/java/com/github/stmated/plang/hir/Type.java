package com.github.stmated.plang.hir;

public record Type(Identifier identifier) {

  public static final Type TYPE_UNKNOWN = new Type(new Identifier("<unknown>"));
  public static final Type TYPE_ANY = new Type(new Identifier("<any>"));

  public static Type getCommonDenominator(Type a, Type b) {

    // TODO: This needs implementation :)
    return a;
  }

  public static Type asArray(final Type type, long expectedSize) {

    // TODO: This needs implementation :)
    return null;
  }

  public static long getAssumedLengthBetween(final Type commonType, final Type a, final Type b) {

    // TODO: This needs implementation :)
    return -1;
  }

  public static Type fromJavaType(final Object literal) {

    // TODO: This needs implementation :)
    return null;
  }

  public static Type getInvertedType(final Type resultType) {

    // TODO: This needs implementation :)
    // Should be the opposite of the incoming. so true => false
    return resultType;
  }
}
