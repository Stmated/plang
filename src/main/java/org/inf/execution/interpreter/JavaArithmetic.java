package org.inf.execution.interpreter;

import org.inf.exceptions.NotImplementedException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.ty.TyValueBoolean;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.TyValueNumberPrecisioned;
import org.inf.ty.TyValueString;

public class JavaArithmetic {

  public Hir.Expression add(Hir.Expression lhs, Hir.Expression rhs) {

//    final var javaLhs = this.lower_literal(lhs);
//    final var javaRhs = this.lower_literal(rhs);
//
//    return switch (literal.ty()) {
//      case TyValueString str -> literal.content();
//      case TyValueNumberInteger ni -> {
//        if (ni.width().value() == 64) {
//          yield Long.parseLong(literal.content(), ni.radix());
//        } else {
//          yield Integer.parseInt(literal.content(), ni.radix());
//        }
//      }
//      case TyValueNumberPrecisioned np -> switch (np.kind()) {
//        case FLOAT -> Float.parseFloat(literal.content());
//        case DOUBLE -> Double.parseDouble(literal.content());
//      };
//      case TyValueBoolean b -> Boolean.parseBoolean(literal.content());
//      default -> throw new UnexpectedExpressionException(literal);
//    };

    throw new NotImplementedException();
  }

  private Object lower_literal(Hir.Literal literal) {

    return switch (literal.ty()) {
      case TyValueString str -> literal.content();
      case TyValueNumberInteger ni -> {
        if (ni.width().value() == 64) {
          yield Long.parseLong(literal.content(), ni.radix());
        } else {
          yield Integer.parseInt(literal.content(), ni.radix());
        }
      }
      case TyValueNumberPrecisioned np -> switch (np.kind()) {
        case FLOAT -> Float.parseFloat(literal.content());
        case DOUBLE -> Double.parseDouble(literal.content());
      };
      case TyValueBoolean b -> Boolean.parseBoolean(literal.content());
      default -> throw new UnexpectedExpressionException(literal);
    };
  }
}
