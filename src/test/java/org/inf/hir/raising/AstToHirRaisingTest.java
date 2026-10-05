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
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.Hir.Call;
import org.inf.hir.Hir.Function;
import org.inf.hir.Hir.Program;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirTransformer;
import org.inf.hir.HirVisitor;
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

import java.util.ArrayList;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
class AstToHirRaisingTest {

  @Test
  void given__typed_tuple_value_entry__when__raised__then__deferred_syntax_is_rejected() {
    Assertions.assertThrows(UnexpectedExpressionException.class, () -> Inf.codeToHir("(a: uint8 = 20,)"));
  }

  @Test
  void given__named_and_mixed_tuple__when__raised__then__labels_are_metadata_not_assignments() {
    final var tuple = Assertions.assertInstanceOf(Hir.Tuple.class, returned("(a = 1, true, b = (2,))"));
    final var nested = Assertions.assertInstanceOf(Hir.Tuple.class, tuple.children()[2].value());
    Assertions.assertAll(
      () -> Assertions.assertEquals(3, tuple.children().length),
      () -> Assertions.assertEquals("a", tuple.children()[0].label().name()),
      () -> Assertions.assertFalse(tuple.children()[0].typeLabel()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, tuple.children()[0].value()),
      () -> Assertions.assertNull(tuple.children()[1].label()),
      () -> Assertions.assertEquals("b", tuple.children()[2].label().name()),
      () -> Assertions.assertEquals(1, nested.children().length),
      () -> Assertions.assertInstanceOf(Hir.Assignment.class, returned("(a = 1)"))
    );
  }

  @ParameterizedTest
  @CsvSource(value = {
    "f(a = 1) | 0",
    "f((a = 1,)) | 1",
    "f(t = (a = 1,)) | 1",
    "f((a = 1, true)) | 1",
    "f((a = 1)) | 0"
  }, delimiter = '|')
  void given__named_tuple_call_boundary__when__raised__then__outer_arguments_remain_separate(
    final String code, final int tuples
  ) {
    final var call = Assertions.assertInstanceOf(Call.class, returned(code));
    final var arguments = HirCallArguments.entries(call.arguments());
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, arguments.size()),
      () -> Assertions.assertEquals(tuples, arguments.stream().filter(it -> it.value() instanceof Hir.Tuple).count())
    );
  }

  @ParameterizedTest
  @CsvSource(value = {
    "f(1,) | f(1)",
    "f((1,),) | f((1,))",
    "(a: 1, b: 2,) | (a: 1, b: 2)",
    "f(a = 1, b = 2,) | f(a = 1, b = 2)",
    "f(a = 1) | f(a = 1)",
    "f(1; 2) | f(1, 2)",
    "(1) | 1",
    "(a: int,) => a | (a: int) => a",
    "(a: int, b: bool,) => a | (a: int, b: bool) => a",
    "(a: int, ...rest,) => a | (a: int, ...rest) => a",
    "(a: int, ...,) => a | (a: int, ...) => a",
    "() => 1 | () => 1",
    "f() | f()",
    "for (var i = (0); i < 3; i += (1)) { f(i,) } | for (var i = 0; i < 3; i += 1) { f(i) }"
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

  @ParameterizedTest
  @CsvSource(value = {
    "(1,) | 1",
    "(1, 2) | 2",
    "(1, 2,) | 2",
    "((1,),) | 1",
    "((1, 2), (3,)) | 2",
    "(a: 1,) | 1",
    "(a: 1, 2) | 2"
  }, delimiter = '|')
  void given__value_parentheses_with_commas__when__raised__then__tuple_entries_are_preserved(
    final String code, final int size
  ) {
    final var ast = Inf.codeToAst(code);
    final var astPrinter = new ToStringTreeAstVisitor();
    final var before = astPrinter.visit(ast);
    final var program = Assertions.assertInstanceOf(
      Program.class, AstToHirRaising.lower_program(ast, new MachineTarget(64))
    );
    final var ret = Assertions.assertInstanceOf(Hir.Return.class, program.expressions());
    final var tuple = Assertions.assertInstanceOf(Hir.Tuple.class, ret.expression());
    Assertions.assertAll(
      () -> Assertions.assertEquals(size, tuple.children().length),
      () -> Assertions.assertEquals(before, astPrinter.visit(ast))
    );
  }

  @Test
  void given__nested_singleton_tuples__when__raised__then__both_tuple_boundaries_remain() {
    final var outer = Assertions.assertInstanceOf(Hir.Tuple.class, returned("((1,),)"));
    final var inner = Assertions.assertInstanceOf(Hir.Tuple.class, outer.children()[0].value());
    final var value = Assertions.assertInstanceOf(Hir.Literal.class, inner.children()[0].value());
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, outer.children().length),
      () -> Assertions.assertEquals(1, inner.children().length),
      () -> Assertions.assertEquals("1", value.content())
    );
  }

  @ParameterizedTest
  @CsvSource(value = {
    "f() | 0 | 0",
    "f(x, y) | 2 | 0",
    "f((x, y)) | 1 | 1",
    "f((x,),) | 1 | 1",
    "f((x, y), (z,)) | 2 | 2",
    "f((x)) | 1 | 0",
    "f(x; y) | 2 | 0",
    "f((x; y)) | 1 | 0",
    "outer(inner x, y) | 1 | 0",
    "outer(inner(x), y) | 2 | 0"
  }, delimiter = '|')
  void given__explicit_call__when__raised__then__only_outer_list_becomes_arguments(
    final String code, final int arity, final int tuples
  ) {
    final var call = Assertions.assertInstanceOf(Call.class, returned(code));
    final var arguments = HirCallArguments.entries(call.arguments());
    Assertions.assertAll(
      () -> Assertions.assertEquals(arity, arguments.size()),
      () -> Assertions.assertEquals(tuples, arguments.stream().filter(it -> it.value() instanceof Hir.Tuple).count())
    );
  }

  @Test
  void given__named_call__when__raised__then__labels_are_argument_metadata() {
    final var call = Assertions.assertInstanceOf(Call.class, returned("f(a = 1, b = 2)"));
    final var arguments = HirCallArguments.entries(call.arguments());
    Assertions.assertAll(
      () -> Assertions.assertEquals(2, arguments.size()),
      () -> Assertions.assertEquals("a", arguments.get(0).label().name()),
      () -> Assertions.assertEquals("b", arguments.get(1).label().name()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, arguments.get(0).value()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, arguments.get(1).value())
    );
  }

  @Test
  void given__tuple_label__when__visited_and_transformed__then__only_value_is_traversed() {
    final var tuple = Assertions.assertInstanceOf(Hir.Tuple.class, returned("(label: value,)"));
    final var entry = tuple.children()[0];
    final var visited = new ArrayList<String>();
    tuple.visit(new HirVisitor() {
      @Override
      public void visitLexeme(final Hir.Lexeme lexeme) {
        visited.add(lexeme.name());
      }

      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        visited.add(identifier.lexeme().name());
      }
    });
    final var transformed = new ArrayList<String>();
    tuple.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformLexeme(final Hir.Lexeme lexeme) {
        transformed.add(lexeme.name());
        return lexeme;
      }

      @Override
      public Hir.Expression transformIdentifier(final Hir.Identifier identifier) {
        transformed.add(identifier.lexeme().name());
        return identifier;
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals("label", entry.label().name()),
      () -> Assertions.assertInstanceOf(Hir.Identifier.class, entry.value()),
      () -> Assertions.assertEquals(java.util.List.of("value"), visited),
      () -> Assertions.assertEquals(java.util.List.of("value"), transformed)
    );
  }

  private Hir.Expression returned(final String code) {
    final var program = Assertions.assertInstanceOf(Program.class, Inf.codeToHir(code));
    return Assertions.assertInstanceOf(Hir.Return.class, program.expressions()).expression();
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
