package com.github.stmated.plang.hir;

import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.util.JavaUtil;

public class HirJavaUtil {

  public static Object resolveLiteralValue(Hir.Expression expr) {

    return switch (expr) {
      case Hir.Literal literal -> switch (literal.ty()) {
        case TyValueNumberInteger vni -> Integer.parseInt(literal.content(), vni.radix());
        default -> null;
      };
      case Hir.BinaryOperation bop -> {
        final var lhs = resolveLiteralValue(bop.lhs());
        final var rhs = resolveLiteralValue(bop.rhs());

        yield switch (bop.kind()) {
          case ADD -> JavaUtil.add(lhs, rhs);
          case SUBTRACT -> JavaUtil.subtract(lhs, rhs);
          case MULTIPLY -> JavaUtil.multiply(lhs, rhs);
          default -> null;
        };
      }
      default -> null;
    };
  }
}
