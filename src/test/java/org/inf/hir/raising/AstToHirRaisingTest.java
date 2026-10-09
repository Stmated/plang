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
import org.inf.hir.HirTransformer;
import org.inf.hir.HirVisitor;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.ast.util.ToStringTreeAstVisitor;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
class AstToHirRaisingTest {

  @Test
  void given__nested_member_syntax__when__raised_directly__then__accesses_contain_only_their_receiver_as_a_child() {
    final var ast = Inf.codeToAst("s.inner.value");
    final var astPrinter = new ToStringTreeAstVisitor();
    final var before = astPrinter.visit(ast);
    final var expression = Assertions.assertInstanceOf(Ast.Expressions.class, ast.children()).children()[0];
    final var outer = Assertions.assertInstanceOf(Hir.DotAccess.class, new AstToHirRaising().raise(expression));
    final var inner = Assertions.assertInstanceOf(Hir.DotAccess.class, outer.target());
    final var receiver = Assertions.assertInstanceOf(Hir.Lexeme.class, inner.target());
    final var visited = new ArrayList<String>();
    outer.visit(new HirVisitor() {
      @Override
      public void visitLexeme(final Hir.Lexeme lexeme) {
        visited.add(lexeme.name());
      }
    });
    final var replacement = new Hir.Lexeme("replacement");
    outer.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformLexeme(final Hir.Lexeme lexeme) {
        return replacement;
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals("value", outer.name()),
      () -> Assertions.assertEquals("inner", inner.name()),
      () -> Assertions.assertEquals("s", receiver.name()),
      () -> Assertions.assertEquals(List.of("s"), visited),
      () -> Assertions.assertSame(replacement, inner.target()),
      () -> Assertions.assertEquals(before, astPrinter.visit(ast))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "s.1",
    "s.true",
    "s.(1 + 2)"
  })
  void given__a_value_instead_of_a_member_name__when__raised__then__the_receiver_is_not_silently_discarded(final String source) {
    Assertions.assertThrows(UnexpectedExpressionException.class, () -> Inf.codeToHir(source));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "s.value[0] | (s.value)[0]",
    "s.value[0][1] | (s.value)[0][1]",
    "s.values[index][0] | (s.values)[index][0]",
    "s.inner.value[0] | (s.inner.value)[0]",
    "s.values[index].value[0] | ((s.values)[index].value)[0]",
    "s.values[index][0] = t | (s.values)[index][0] = t",
    "s.values[t.indices[0]] | (s.values)[(t.indices)[0]]"
  })
  void given__indexed_member_syntax__when__raised__then__indexing_targets_the_complete_member(
    final String code, final String equivalent
  ) {
    final var printer = new ToStringTreeHirVisitor();
    Assertions.assertEquals(printer.render(Inf.codeToHir(equivalent)), printer.render(Inf.codeToHir(code)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "s.call() | (s.call)()",
    "s.call(1, 2) | (s.call)(1, 2)",
    "s.call(b = value, a = 1) | (s.call)(b = value, a = 1)",
    "s.call a = 1, b = value | (s.call)(a = 1, b = value)",
    "s.call(...args) | (s.call)(...args)",
    "s.inner.call(1) | (s.inner.call)(1)",
    "s.values[index].call(value) | ((s.values)[index].call)(value)",
    "(s.calls[index])(value) | ((s.calls)[index])(value)",
    "s.make().call(value) | ((s.make)().call)(value)",
    "(s.make())(value) | ((s.make)())(value)",
    "s.make().values[index] | ((s.make)().values)[index]",
    "s.call(t.value) | (s.call)(t.value)"
  })
  void given__called_member_syntax__when__raised__then__only_the_target_uses_the_complete_member_receiver(
    final String code, final String equivalent
  ) {
    final var printer = new ToStringTreeHirVisitor();
    Assertions.assertEquals(printer.render(Inf.codeToHir(equivalent)), printer.render(Inf.codeToHir(code)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "~(s.call(1)) | s",
    "~((s.call)(1)) | s",
    "~(s.call value) | s",
    "~(s.inner.call(1)) | s.inner",
    "~(s.inner.call value) | s.inner"
  })
  void given__partial_member_call__when__raised__then__the_call_keeps_its_complete_receiver_and_partial_flag(
    final String code, final String receiver
  ) {
    final var call = Assertions.assertInstanceOf(Hir.Call.class, returned(code));
    final var member = Assertions.assertInstanceOf(Hir.DotAccess.class, call.target());
    Assertions.assertAll(
      () -> Assertions.assertTrue(call.partial()),
      () -> Assertions.assertEquals("call", member.name()),
      () -> Assertions.assertEquals(1, call.arguments().length),
      () -> Assertions.assertEquals(receiver, member.target().toString())
    );
  }

  @Test
  void given__explicit_and_omitted_annotations__when__raised__then__source_syntax_is_preserved_without_type_resolution() {
    final var root = Inf.codeToHir("val v: int = 7; val use = (p: bool, q): bool => p; [7;int;1]; [7]");
    final var annotations = new ArrayList<Hir.DynamicTy>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        annotations.add(declaration.typeAnnotation());
        HirVisitor.super.visitDec(declaration);
      }

      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        annotations.add(parameter.typeAnnotation());
        HirVisitor.super.visitParameter(parameter);
      }

      @Override
      public void visitFunctionSignatureReturnType(final Hir.DynamicTy annotation) {
        annotations.add(annotation);
        HirVisitor.super.visitFunctionSignatureReturnType(annotation);
      }

      @Override
      public void visitArrayElementType(final Hir.DynamicTy annotation) {
        annotations.add(annotation);
        HirVisitor.super.visitArrayElementType(annotation);
      }
    });
    final var explicit = annotations.stream().filter(annotation -> annotation.expression() != null)
      .map(annotation -> Assertions.assertInstanceOf(Hir.Identifier.class, annotation.expression())).toList();
    Assertions.assertAll(
      () -> Assertions.assertEquals(7, annotations.size()),
      () -> Assertions.assertTrue(annotations.stream().allMatch(annotation -> annotation.ty() == Ty.INFER)),
      () -> Assertions.assertEquals(java.util.List.of("int", "bool", "bool", "int"),
        explicit.stream().map(Hir.Identifier::name).toList()),
      () -> Assertions.assertTrue(explicit.stream().allMatch(identifier -> identifier.ty() == null)),
      () -> Assertions.assertTrue(explicit.stream().allMatch(identifier -> identifier.target() == null))
    );
  }

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
    final var arguments = call.arguments();
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, arguments.length),
      () -> Assertions.assertEquals(tuples, Arrays.stream(arguments).filter(it -> it.value() instanceof Hir.Tuple).count())
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
    final var hir = AstToHirRaising.lower_program(ast);

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
      Program.class, AstToHirRaising.lower_program(ast)
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
    final var arguments = call.arguments();
    Assertions.assertAll(
      () -> Assertions.assertEquals(arity, arguments.length),
      () -> Assertions.assertEquals(tuples, Arrays.stream(arguments).filter(it -> it.value() instanceof Hir.Tuple).count())
    );
  }

  @Test
  void given__named_call__when__raised__then__labels_are_argument_metadata() {
    final var call = Assertions.assertInstanceOf(Call.class, returned("f(a = 1, b = 2)"));
    final var arguments = call.arguments();
    Assertions.assertAll(
      () -> Assertions.assertEquals(2, arguments.length),
      () -> Assertions.assertEquals("a", arguments[0].label().name()),
      () -> Assertions.assertEquals("b", arguments[1].label().name()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, arguments[0].value()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, arguments[1].value())
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
        visited.add(identifier.name());
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
        transformed.add(identifier.name());
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
      () -> AstToHirRaising.lower_program(raw)
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

    final var hir = new AstToHirRaising().lower_conditional(ast);

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
