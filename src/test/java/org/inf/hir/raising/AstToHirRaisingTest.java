package org.inf.hir.raising;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import org.inf.Inf;
import org.inf.ast.Ast;
import org.inf.ast.Ast.BinaryOperation;
import org.inf.ast.Ast.BinaryOperationKind;
import org.inf.ast.Ast.Block;
import org.inf.ast.Ast.Conditional;
import org.inf.ast.Ast.Expression;
import org.inf.ast.Ast.Literal;
import org.inf.ast.Ast.Return;
import org.inf.hir.AstToHirRaising;
import org.inf.hir.Hir;
import org.inf.hir.Hir.Call;
import org.inf.hir.Hir.Function;
import org.inf.hir.Hir.Program;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
class AstToHirRaisingTest {

  @ParameterizedTest
  @CsvSource(value = {
    "function_reference | f",
    "explicit_call | f(x, y)",
    "adjacent_expressions | if (true) 1 2",
    "subtraction | f - 1",
    "indexing | f[0]"
  }, delimiter = '|')
  void given__existing_syntax__when__raised__then__expected_hir(
    final String name, final String code, final TestInfo testInfo, final Snapshot snapshot
  ) {
    SnapshotTestUtils.assertMatches(testInfo, snapshot, name, new ToStringTreeHirVisitor().render(Inf.codeToHir(code)));
  }

  @Test
  void lowerConditional() {

    final var ast = new Conditional(
      new BinaryOperation(
        new Literal("1", Ty.INTEGER),
        BinaryOperationKind.EQUALS,
        new Literal("1", Ty.INTEGER)
      ),
      new Block(
        new Ast.Expressions(
          new Expression[]{
            new Return(
              new Literal("10", Ty.INTEGER)
            )
          }
        )
      ),
      new Block(
        new Ast.Expressions(
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
    final var hir = Inf.codeToHir(code);

    Assertions.assertNotNull(hir);

    Assertions.assertInstanceOf(Program.class, hir);
  }

  @Test
  void testAnonymousFnWithDirectCall() {

    final var thir = Inf.codeToThir("((a: int, b: int) => a + b)(5, 5)");
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
