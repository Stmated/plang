package org.inf.hir;

import org.inf.ty.TyValueNumberInteger;
import org.inf.util.JavaUtil;

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
