package com.github.stmated.plang.hir.raising;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.ast.Ast.BinaryOperation;
import com.github.stmated.plang.ast.Ast.BinaryOperationKind;
import com.github.stmated.plang.ast.Ast.Block;
import com.github.stmated.plang.ast.Ast.Conditional;
import com.github.stmated.plang.ast.Ast.Expression;
import com.github.stmated.plang.ast.Ast.Expressions;
import com.github.stmated.plang.ast.Ast.Literal;
import com.github.stmated.plang.ast.Ast.Return;
import com.github.stmated.plang.hir.AstToHirRaising;
import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.Hir.Call;
import com.github.stmated.plang.hir.Hir.Function;
import com.github.stmated.plang.hir.Hir.Program;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.util.MachineTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AstToHirRaisingTest {

  @Test
  void lowerConditional() {

    final var ast = new Conditional(
      new BinaryOperation(
        new Literal("1", Ty.INTEGER),
        BinaryOperationKind.EQUALS,
        new Literal("1", Ty.INTEGER)
      ),
      new Block(
        new Expressions(
          new Expression[]{
            new Return(
              new Literal("10", Ty.INTEGER)
            )
          }
        )
      ),
      new Block(
        new Expressions(
          new Expression[]{
            new Return(
              new Literal("20", Ty.INTEGER)
            )
          }
        )
      )
    );

    final var hir = new AstToHirRaising(new MachineTarget(64)).lower_conditional(ast);

    Assertions.assertInstanceOf(Hir.BinaryOperation.class, hir.predicate());

    final var hbo = (Hir.BinaryOperation) hir.predicate();
    Assertions.assertInstanceOf(Hir.Literal.class, hbo.lhs());
    Assertions.assertEquals(Hir.BinaryOperationKind.EQUALS, hbo.kind());
    Assertions.assertInstanceOf(Hir.Literal.class, hbo.lhs());
  }

  @Test
  void testCreateAndAccessArray() {
    final var code = "a[10]";
    final var hir = Plang.codeToHir(code);

    Assertions.assertNotNull(hir);

    Assertions.assertInstanceOf(Program.class, hir);
  }

  @Test
  void testAnonymousFnWithDirectCall() {

    final var thir = Plang.codeToThir("((a: int, b: int) => a + b)(5, 5)");
    final var hir = thir.root();
    Assertions.assertNotNull(hir);
    Assertions.assertInstanceOf(Program.class, hir);
    final var program = ((Program) hir);
    Assertions.assertInstanceOf(Hir.Return.class, program.expressions());
    final var ret = ((Hir.Return) program.expressions());
    Assertions.assertInstanceOf(Call.class, ret.expression());
    final var call = ((Call) ret.expression());
    Assertions.assertInstanceOf(Function.class, call.target());
  }
}
