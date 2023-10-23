package com.github.stmated.plang.hir.lowering;

import com.github.stmated.plang.ast.model.AstBinaryOperation;
import com.github.stmated.plang.ast.model.AstBinaryOperationKind;
import com.github.stmated.plang.ast.model.AstBlock;
import com.github.stmated.plang.ast.model.AstConditional;
import com.github.stmated.plang.ast.model.AstExpression;
import com.github.stmated.plang.ast.model.AstLiteral;
import com.github.stmated.plang.ast.model.AstReturn;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBinaryOperationKind;
import com.github.stmated.plang.hir.model.HirLiteral;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AstToHirLoweringTest {

  @Test
  void lowerConditional() {

    final var ast = new AstConditional(
      new AstBinaryOperation(
        new AstLiteral(1),
        AstBinaryOperationKind.EQUALS,
        new AstLiteral(1)
      ),
      new AstBlock(
        new AstExpression[] {
          new AstReturn(
            new AstLiteral(10)
          )
        }
      ),
      new AstBlock(
        new AstExpression[] {
          new AstReturn(
            new AstLiteral(20)
          )
        }
      )
    );

    final var hir = AstToHirLowering.lower_conditional(ast);

    Assertions.assertInstanceOf(HirBinaryOperation.class, hir.predicate());

    final var hbo = (HirBinaryOperation) hir.predicate();
    Assertions.assertInstanceOf(HirLiteral.class, hbo.lhs());
    Assertions.assertEquals(HirBinaryOperationKind.EQUALS, hbo.type());
    Assertions.assertInstanceOf(HirLiteral.class, hbo.lhs());
  }
}
