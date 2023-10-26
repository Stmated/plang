package com.github.stmated.plang.mir;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.mir.model.MirExpression;
import com.github.stmated.plang.mir.model.MirExpressions;
import com.github.stmated.plang.mir.model.MirProgram;
import com.github.stmated.plang.mir.model.MirReturn;

public class HirToMirLowering {

  public MirProgram lower_program(HirProgram hir) {
    return new MirProgram(
      new MirExpressions(lower_expressions(hir.expressions()))
    );
  }

  private MirExpression[] lower_expressions(HirExpression[] expressions) {

    final var lowered = new MirExpression[expressions.length];
    for (var i = 0; i < expressions.length; i++) {
      lowered[i] = lower_expression(expressions[i]);
    }

    return lowered;
  }

  private MirExpression lower_expression(HirExpression hir) {

    return switch (hir) {
      case HirReturn v -> lower_return(v);
      default -> throw new NotImplementedException(STR."Do not know how to handle '\{hir}'");
    };
  }

  private MirReturn lower_return(HirReturn hir) {
    return new MirReturn(lower_expression(hir.expression()));
  }
}
