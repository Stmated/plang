package com.github.stmated.plang.hir.raising;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.ast.model.AstBinaryOperation;
import com.github.stmated.plang.ast.model.AstBinaryOperationKind;
import com.github.stmated.plang.ast.model.AstBlock;
import com.github.stmated.plang.ast.model.AstConditional;
import com.github.stmated.plang.ast.model.AstExpression;
import com.github.stmated.plang.ast.model.AstExpressions;
import com.github.stmated.plang.ast.model.AstLiteral;
import com.github.stmated.plang.ast.model.AstReturn;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBinaryOperationKind;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirFunction;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.ty.Ty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AstToHirRaisingTest {

  @Test
  void lowerConditional() {

    final var ast = new AstConditional(
      new AstBinaryOperation(
        new AstLiteral("1", Ty.INTEGER),
        AstBinaryOperationKind.EQUALS,
        new AstLiteral("1", Ty.INTEGER)
      ),
      new AstBlock(
        new AstExpressions(
          new AstExpression[]{
            new AstReturn(
              new AstLiteral("10", Ty.INTEGER)
            )
          }
        )
      ),
      new AstBlock(
        new AstExpressions(
          new AstExpression[]{
            new AstReturn(
              new AstLiteral("20", Ty.INTEGER)
            )
          }
        )
      )
    );

    final var hir = new AstToHirRaising().lower_conditional(ast);

    Assertions.assertInstanceOf(HirBinaryOperation.class, hir.predicate());

    final var hbo = (HirBinaryOperation) hir.predicate();
    Assertions.assertInstanceOf(HirLiteral.class, hbo.lhs());
    Assertions.assertEquals(HirBinaryOperationKind.EQUALS, hbo.kind());
    Assertions.assertInstanceOf(HirLiteral.class, hbo.lhs());
  }

  @Test
  void testCreateAndAccessArray() {
    final var code = "a[10]";
    final var hir = Plang.codeToHir(code);

    Assertions.assertNotNull(hir);

    Assertions.assertInstanceOf(HirProgram.class, hir);
  }

  @Test
  void testAnonymousFnWithDirectCall() {

    final var thir = Plang.codeToThir("((a: int, b: int) => a + b)(5, 5)");
    final var hir = thir.root();
    Assertions.assertNotNull(hir);
    Assertions.assertInstanceOf(HirProgram.class, hir);
    final var program = ((HirProgram) hir);
    Assertions.assertInstanceOf(HirReturn.class, program.expressions());
    final var ret = ((HirReturn) program.expressions());
    Assertions.assertInstanceOf(HirCall.class, ret.expression());
    final var call = ((HirCall) ret.expression());
    Assertions.assertInstanceOf(HirFunction.class, call.target());
  }
}
