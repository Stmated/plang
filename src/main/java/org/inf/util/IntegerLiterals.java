package org.inf.util;

import java.math.BigInteger;

public final class IntegerLiterals {

  private IntegerLiterals() {
  }

  public static BigInteger parse(String content, int radix) {
    var digits = content.replace("_", "").replaceFirst("(?i)([iu]\\d+|l)$", "");
    final var prefix = switch (radix) {
      case 2 -> "b";
      case 8 -> "o";
      case 16 -> "x";
      default -> null;
    };
    if (prefix != null) {
      digits = digits.replaceFirst("(?i)^([+-]?)0" + prefix, "$1");
    }
    return new BigInteger(digits, radix);
  }
}
