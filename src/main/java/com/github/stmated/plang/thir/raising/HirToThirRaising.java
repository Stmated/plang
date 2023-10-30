package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.util.TyUtil;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO: This should be rewritten to use some kind of query system like Rust, eventually.
 *        That way we can easier multi-thread the investigation, and also do it lazily on-demand, and cache more easily.
 */
public class HirToThirRaising {

  private final Map<HirExpression, Ty> map = new HashMap<>();

  public Ty getType(HirExpression e) {
    return map.get(e);
  }

  public Ty investigate(HirExpression e) {

    final var existing = map.get(e);
    if (existing != null) {
      return existing;
    }

    final var ty = investigate_inner(e);

    return map.put(e, ty);
  }

  private Ty investigate_inner(HirExpression e) {

    return switch (e) {
      case HirProgram hir -> investigate_program(hir);
      case HirBinaryOperation hir -> investigate_binary_operation(hir);
      case HirLiteral hir -> investigate_literal(hir);
      default -> throw new UnexpectedExpressionException(e);
    };
  }

  private Ty investigate_literal(HirLiteral hir) {


    return null;
  }

  private Ty investigate_program(HirProgram hir) {
    return investigate_expressions(hir.expressions());
  }

  private Ty investigate_expressions(HirExpression[] expressions) {

    // NOTE: Most likely this is not correct?
    if (expressions.length > 0) {

      final var lastExpression = expressions[expressions.length - 1];
      return investigate(lastExpression);
    }

    throw new IllegalArgumentException(STR."Could not find type of '\{expressions}'");
  }

  private Ty investigate_binary_operation(HirBinaryOperation v) {

    final var lhs = investigate(v.lhs());
    final var rhs = investigate(v.rhs());

    final var result = TyUtil.getCommonDenominator(lhs, rhs);
    if (result.type() == null) {
      throw new IllegalArgumentException(STR."There was no common denominator between '\{lhs}' and '\{rhs}'");
    }

    return result.type();
  }
}
