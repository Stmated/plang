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
import org.inf.ast.util.ToStringTreeAstVisitor;
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
    "(1,) | (1)",
    "(1, 2,) | (1; 2)",
    "((1,),) | 1",
    "((1, 2), (3,)) | ((1; 2); 3)",
    "f((1, 2)) | f(1, 2)",
    "f(1,) | f(1)",
    "f((1,),) | f(1)",
    "(a: 1,) | (a: 1)",
    "(a: 1, b: 2,) | (a: 1, b: 2)",
    "f(a: 1, b: 2,) | f(a: 1, b: 2)",
    "(a: int,) => a | (a: int) => a",
    "(a: int, b: bool,) => a | (a: int, b: bool) => a",
    "(a: int, ...rest,) => a | (a: int, ...rest) => a",
    "(a: int, ...,) => a | (a: int, ...) => a",
    "() => 1 | () => 1",
    "f() | f()",
    "for (var i = (0,); i < 3; i += (1,)) { f(i,) } | for (var i = 0; i < 3; i += 1) { f(i) }"
  }, delimiter = '|')
  void given__parenthesized_comma_nodes__when__raised__then__legacy_hir_behavior_is_preserved(
    final String code, final String equivalent
  ) {
    final var printer = new ToStringTreeHirVisitor();
    final var ast = Inf.codeToAst(code);
    final var astPrinter = new ToStringTreeAstVisitor();
    final var before = astPrinter.visit(ast);
    final var hir = AstToHirRaising.lower_program(ast, new MachineTarget(64));

    Assertions.assertAll(
      () -> Assertions.assertEquals(printer.render(Inf.codeToHir(equivalent)), printer.render(hir)),
      () -> Assertions.assertEquals(before, astPrinter.visit(ast))
    );
  }

  @Test
  void given__comma_outside_parentheses__when__raised__then__rejected() {
    final var raw = Inf.codeToRawAst("1, 2");
    Assertions.assertThrows(
      IllegalArgumentException.class,
      () -> AstToHirRaising.lower_program(raw, new MachineTarget(64))
    );
  }

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
