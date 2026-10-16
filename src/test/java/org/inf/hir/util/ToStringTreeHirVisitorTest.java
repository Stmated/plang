package org.inf.hir.util;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import org.inf.Inf;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.hir.DynamicTy;
import org.inf.hir.DynamicTyDefault;
import org.inf.hir.DynamicTyRedirector;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.*;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
class ToStringTreeHirVisitorTest {

  private final ToStringTreeHirVisitor printer = new ToStringTreeHirVisitor();

  @Test
  void given__explicit_conversion__when__rendered__then__destination_and_operand_are_preserved() {
    final var conversions = new ArrayList<Hir.Convert>();
    Inf.codeToThir("val t: (int16,) = (255u8,); t").root().visit(new HirVisitor() {
      @Override
      public void visitConvert(Hir.Convert conversion) {
        conversions.add(conversion);
      }
    });
    assertEquals("""
      (Convert
        (ty (number INTEGER EXPLICIT_16 signed=true radix=10))
        (targetTy (number INTEGER EXPLICIT_16 signed=true radix=10))
        (expression
          (Literal
            (content "255u8")
            (ty (number INTEGER EXPLICIT_8 signed=false radix=10)))))""",
      printer.render(conversions.getFirst()));
  }

  @Test
  void given__nested_expressions__when__rendered__then__exact_ordered_structure() {
    final var expression = new Hir.Block(new Hir.Expressions(new Hir.Expression[] {
      new Hir.Lexeme("first"),
      new Hir.Lexeme("second")
    }), null);

    assertEquals("""
      (Block
        (ty null)
        (children
          (Expressions
            (generated false)
            (ty null)
            (children
              (Lexeme
                (name "first")
                (ty VOID))
              (Lexeme
                (name "second")
                (ty VOID))))))""", printer.render(expression));
  }

  @Test
  void given__quoted_scalar_data__when__rendered__then__escaped_without_literal_control_characters() {
    assertEquals("""
      (Literal
        (content "quote:\\" slash:\\\\ newline:\\n return:\\r tab:\\t backspace:\\b formfeed:\\f nul:\\u0000")
        (ty STRING))""",
      printer.render(new Hir.Literal("quote:\" slash:\\ newline:\n return:\r tab:\t backspace:\b formfeed:\f nul:\0", Ty.STRING)));
  }

  @Test
  void given__missing_and_empty_values__when__rendered__then__distinguished() {
    assertAll(
      () -> assertEquals("null", printer.render((Hir.Expression) null)),
      () -> assertEquals("""
        (Expressions
          (generated false)
          (ty null)
          (children null))""", printer.render(new Hir.Expressions(null))),
      () -> assertEquals("""
        (Expressions
          (generated false)
          (ty null)
          (children))""", printer.render(new Hir.Expressions(new Hir.Expression[0]))),
      () -> assertNotEquals(printer.render(new Hir.Literal(null, null)), printer.render(new Hir.Literal("", null))),
      () -> assertEquals("""
        (Call
          (partial false)
          (ty null)
          (target
            (Lexeme
              (name "f")
              (ty VOID)))
          (arguments))""", printer.render(new Hir.Call(new Hir.Lexeme("f"), new Hir.Argument[0]))),
      () -> assertNotEquals(printer.render(new Hir.Conditional(new Hir.Lexeme("p"), new Hir.Lexeme("yes"), null)),
        printer.render(new Hir.Conditional(new Hir.Lexeme("p"), new Hir.Lexeme("yes"), new Hir.Expressions(new Hir.Expression[0]))))
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__call_flags_and_labels__when__rendered__then__preserved(
    final boolean partial, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var call = new Hir.Call(new Hir.Identifier("f", null), new Hir.Argument[] {
      new Hir.Argument(new Hir.Lexeme("label"), new Hir.Literal("1", Ty.INTEGER)),
      new Hir.Argument(null, new Hir.Literal("2", Ty.INTEGER))
    }, partial, null);

    SnapshotTestUtils.assertMatches(testInfo, snapshot, Boolean.toString(partial), printer.render(call));
    assertNotEquals(printer.render(call), printer.render(new Hir.Call(call.target(), call.arguments(), !partial, null)));
  }

  @ParameterizedTest
  @EnumSource(Hir.BinaryOperationKind.class)
  void given__operator_kind__when__rendered__then__binary_and_compound_kinds_are_explicit(final Hir.BinaryOperationKind kind) {
    final var left = new Hir.Lexeme("left");
    final var right = new Hir.Lexeme("right");
    assertAll(
      () -> assertEquals("""
        (BinaryOperation
          (kind %s)
          (ty null)
          (lhs
            (Lexeme
              (name "left")
              (ty VOID)))
          (rhs
            (Lexeme
              (name "right")
              (ty VOID))))""".formatted(kind), printer.render(new Hir.BinaryOperation(left, kind, right))),
      () -> assertEquals("""
        (CompoundAssignment
          (kind %s)
          (ty null)
          (target
            (Lexeme
              (name "left")
              (ty VOID)))
          (rhs
            (Lexeme
              (name "right")
              (ty VOID))))""".formatted(kind), printer.render(new Hir.CompoundAssignment(left, kind, right)))
    );
  }

  @ParameterizedTest
  @EnumSource(Hir.MutabilityKind.class)
  void given__declaration_mutability__when__rendered__then__preserved(
    final Hir.MutabilityKind kind, final TestInfo testInfo, final Snapshot snapshot
  ) {
    SnapshotTestUtils.assertMatches(testInfo, snapshot, kind.name(),
      printer.render(new Hir.Dec(new Hir.Lexeme("value"), kind, new DynamicTyDefault(Ty.INTEGER))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__varargs_and_generated_sequence_flags__when__rendered__then__preserved(
    final boolean flag, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), Hir.BuiltInTy.fromString("int", MachineTarget.T64), flag, Ty.INTEGER);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[] { parameter }, flag, new DynamicTyDefault());
    final var expressions = new Hir.Expressions(new Hir.Expression[] {
      new Hir.Function(signature, new Hir.Return(new Hir.Identifier(parameter.lexeme().name(), parameter)))
    }, null, flag);

    SnapshotTestUtils.assertMatches(testInfo, snapshot, Boolean.toString(flag), printer.render(expressions));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "nested_calls | f(g(1), label = 2)",
    "conditional | if (true) { 1; 2; } else 3",
    "array | val a = [1, 2]; a[0]",
    "function | val f = (x: int) => x + 1; f(2)",
    "generated_loop | for (var i = 0; i < 3; i += 1) { continue; }"
  }, delimiter = '|')
  void given__source__when__hir_rendered__then__structural_snapshot(
    final String name, final String code, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var expression = Inf.codeToHir(code);
    final var actual = printer.render(expression);
    SnapshotTestUtils.assertMatches(testInfo, snapshot, name, actual);
    assertEquals(actual, printer.render(Inf.codeToHir(code)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "function | val f = (x: int) => x + 1; f(2)",
    "array | var a = [1, 2]; a[0] = 9; return a[0];",
    "diverging_array | val use = () => [{ return true; }, 7]; use()",
    "diverging_index | val use = () => [7][{ return true; }]; use()"
  })
  void given__typed_source__when__rendered_repeatedly__then__stable_and_terminating(
    final String name, final String code, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var expression = Inf.codeToThir(code).root();
    final var actual = assertTimeout(Duration.ofSeconds(5), () -> printer.render(expression));
    printer.render(new Hir.Lexeme("unrelated"));
    assertAll(
      () -> assertEquals(actual, printer.render(expression)),
      () -> assertEquals(actual, new ToStringTreeHirVisitor().render(expression))
    );
    SnapshotTestUtils.assertMatches(testInfo, snapshot, name, actual);
  }

  static Stream<Arguments> completionTypes() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, "(ty INTEGER)"),
      Arguments.of(new TyInfo(Ty.INTEGER, Ty.INTEGER, null), "(ty INTEGER)"),
      Arguments.of(
        new TyInfo(new TyValueArray(Ty.INTEGER, 1), new TyValueArray(Ty.INTEGER, 1), null),
        "(ty (array size=1 INTEGER))"
      ),
      Arguments.of(new TyInfo(null, null, null), "(ty null)"),
      Arguments.of(new TyInfo(null, Ty.INTEGER, null), "(ty (completion null structural INTEGER))"),
      Arguments.of(new TyInfo(Ty.INTEGER, null, null), "(ty INTEGER)"),
      Arguments.of(new TyInfo(Ty.INTEGER, Ty.LONG, null), "(ty (completion INTEGER structural LONG))"),
      Arguments.of(
        Tys.withCompletion(Ty.DEADEND, new TyValueArray(Ty.INTEGER, 1)),
        "(ty (completion DEADEND structural (array size=1 INTEGER)))"
      ),
      Arguments.of(Tys.withCompletion(Ty.DEADEND, null), "(ty DEADEND)"),
      Arguments.of(Tys.typeInfo(Ty.INTEGER, Ty.INTEGER, Ty.INFER), "(ty INTEGER)"),
      Arguments.of(Tys.typeInfo(Ty.VOID, Ty.DEADEND, Ty.INFER), "(ty VOID)"),
      Arguments.of(Tys.typeInfo(Ty.VOID, Ty.DEADEND, null), "(ty VOID)"),
      Arguments.of(Tys.typeInfo(Ty.VOID, null, Ty.BOOLEAN), "(ty VOID)")
    );
  }

  @ParameterizedTest
  @MethodSource("completionTypes")
  void given__completion_projections__when__rendered__then__only_distinct_projections_are_printed(
    final Ty type, final String expected
  ) {
    assertEquals(expected, printer.render(type));
  }

  @Test
  void given__recursive_type__when__rendered__then__bounded_without_identity_addresses() {
    final var fields = new TyField[1];
    final var type = new TyStruct(fields, false);
    fields[0] = new TyField("next", new TyPointer<>(type));

    assertEquals("""
      (DynamicTy
        (ty (struct ("next" (pointer CPU (recursive)))))
        (expression null))""",
      assertTimeout(Duration.ofSeconds(1), () -> printer.render(new DynamicTyDefault(type))));
  }

  @Test
  void given__resolved_alias_constraint__when__rendered__then__cached_type_and_source_expression_are_both_visible() {
    final var source = new Hir.Identifier("Alias", null);
    assertEquals("""
      (DynamicTy
        (ty BOOLEAN)
        (expression
          (Identifier
            (name "Alias")
            (target null))))""", printer.render(new DynamicTyDefault(Ty.BOOLEAN, source)));
  }

  @Test
  void given__inferred_binding_and_contextual_tuple__when__rendered__then__specific_type_metadata_is_preserved() {
    final var declarations = new ArrayList<Hir.Dec>();
    final var tuples = new ArrayList<Hir.Tuple>();
    Inf.codeToThir("val n = 7; val pair: (value: uint8,) = (value = 1,); pair").root().visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declarations.add(declaration);
        HirVisitor.super.visitDec(declaration);
      }

      @Override
      public void visitTuple(final Hir.Tuple tuple) {
        if (tuple.contextualType() != null) {
          tuples.add(tuple);
        }
        HirVisitor.super.visitTuple(tuple);
      }
    });

    final var binding = printer.render(declarations.getFirst());
    final var tuple = printer.render(tuples.getFirst());
    assertTrue(binding.contains("(ty (completion VOID structural INTEGER))"));
    assertTrue(binding.contains("(typeAnnotation\n    (DynamicTy\n      (ty INFER)\n      (expression null)))"));
    assertTrue(tuple.contains("(contextualType (struct (\"value\" CHAR"));
    assertFalse(binding.contains("valueTy"));
    assertFalse(tuple.contains("valueTy"));
  }

  @Test
  void given__deep_finite_types__when__rendered__then__inner_type_differences_are_preserved() {
    Ty integer = Ty.INTEGER;
    Ty string = Ty.STRING;
    for (var i = 0; i < 20; i++) {
      integer = new TyPointer<>(integer);
      string = new TyPointer<>(string);
    }
    assertNotEquals(printer.render(integer), printer.render(string));
  }

  @Test
  void given__remaining_node_shapes__when__rendered__then__all_child_roles_are_preserved(
    final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var name = new Hir.Lexeme("name");
    final var identifier = new Hir.Identifier(name.name(), null);
    final var value = new Hir.Literal("7", Ty.INTEGER);
    final var declaration = new Hir.Dec(name, Hir.MutabilityKind.CONSTANT, new DynamicTyDefault(Ty.INTEGER));
    final var initializer = new Hir.Assignment(name, value);
    final var array = new Hir.Array(new Hir.Expression[] { value }, new DynamicTyDefault(Ty.INTEGER), value,
      Tys.withCompletion(Ty.VOID, new TyValueArray(Ty.INTEGER, 1)));
    final var nodes = new Hir.Expression[] {
      array,
      new Hir.ArrayAccess(identifier, value, Tys.withCompletion(Ty.VOID, Ty.INTEGER)),
      new Hir.Labeling(name, value, Ty.INTEGER),
      new Hir.Loop(new Hir.Block(new Hir.LoopBreak(value), Ty.DEADEND)),
      new Hir.LoopContinue(),
      new Hir.NewByBlock(identifier, identifier, new Hir.Assignment[] { initializer }, Ty.VOID),
      new Hir.NewByCtor(identifier, null, new Hir.Tuple(new Hir.TupleEntry[0], null), null),
      new Hir.Not(value, Ty.BOOLEAN),
      new Hir.DotAccess(identifier, name.name()),
      new Hir.Range(value, new Hir.Literal("9", Ty.INTEGER)),
      new Hir.DeadEnd(new Hir.Return(value)),
      new Hir.Struct(new Hir.Dec[] { declaration }, null),
      new Hir.Trait(new Hir.Expression[] { declaration }, null),
      new Hir.Tuple(new Hir.TupleEntry[] {
        new Hir.TupleEntry(name, value),
        new Hir.TupleEntry(null, value)
      }, null),
      Hir.BuiltInTy.fromString("int", MachineTarget.T64)
    };

    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, printer.render(new Hir.Program(new Hir.Expressions(nodes))));
  }

  @Test
  void given__composite_type_metadata__when__rendered__then__stable_structural_types() {
    final Ty[] types = {
      Ty.UNKNOWN, new TyOpaque(),
      Ty.INTEGER_BINARY, Ty.FLOAT, Ty.DECIMAL, Ty.BOOLEAN, Ty.STRING,
      new TyValueArray(Ty.INTEGER, null), new TyValueArray(Ty.INTEGER, 0),
      new TyUninitialized<>(Ty.INTEGER), new TyPointerExplicit(Ty.INTEGER),
      new TyUnion(new Ty[] { Ty.INTEGER, Ty.STRING }),
      new TyFn(new TyParam[] { new TyParam("p", Ty.INTEGER) }, true, Ty.VOID),
      new TyStruct(new TyField[] { new TyField("field", Ty.STRING) })
    };
    final String[] expected = {
      "(ty UNKNOWN)",
      "(ty (opaque))",
      "(ty INTEGER_BINARY)",
      "(ty FLOAT)",
      "(ty DECIMAL)",
      "(ty BOOLEAN)",
      "(ty STRING)",
      "(ty (array size=null INTEGER))",
      "(ty (array size=0 INTEGER))",
      "(ty (uninitialized INTEGER))",
      "(ty (explicit-pointer CPU INTEGER))",
      "(ty (union INTEGER STRING))",
      "(ty (function vararg=true (parameters (\"p\" INTEGER)) (returns VOID)))",
      "(ty (struct (\"field\" STRING)))"
    };
    for (int i = 0; i < types.length; i++) {
      assertEquals(expected[i], printer.render(types[i]));
    }
  }
}
