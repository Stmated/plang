package org.inf.hir.util;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import org.inf.Inf;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.*;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;

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
        (ty (number INTEGER width=16 explicit=true signed=true radix=10 flags=[]))
        (targetTy (number INTEGER width=16 explicit=true signed=true radix=10 flags=[]))
        (expression
          (Literal
            (content "255u8")
            (ty (number INTEGER width=8 explicit=true signed=false radix=10 flags=[])))))""",
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
                (ty (named "VOID")))
              (Lexeme
                (name "second")
                (ty (named "VOID")))))))""", printer.render(expression));
  }

  @Test
  void given__quoted_scalar_data__when__rendered__then__escaped_without_literal_control_characters() {
    assertEquals("""
      (Literal
        (content "quote:\\" slash:\\\\ newline:\\n return:\\r tab:\\t backspace:\\b formfeed:\\f nul:\\u0000")
        (ty (string)))""",
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
              (ty (named "VOID"))))
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
              (ty (named "VOID"))))
          (rhs
            (Lexeme
              (name "right")
              (ty (named "VOID")))))""".formatted(kind), printer.render(new Hir.BinaryOperation(left, kind, right))),
      () -> assertEquals("""
        (CompoundAssignment
          (kind %s)
          (ty null)
          (target
            (Lexeme
              (name "left")
              (ty (named "VOID"))))
          (rhs
            (Lexeme
              (name "right")
              (ty (named "VOID")))))""".formatted(kind), printer.render(new Hir.CompoundAssignment(left, kind, right)))
    );
  }

  @ParameterizedTest
  @EnumSource(Hir.MutabilityKind.class)
  void given__declaration_mutability__when__rendered__then__preserved(
    final Hir.MutabilityKind kind, final TestInfo testInfo, final Snapshot snapshot
  ) {
    SnapshotTestUtils.assertMatches(testInfo, snapshot, kind.name(),
      printer.render(new Hir.Dec(new Hir.Lexeme("value"), kind, new Hir.DynamicTy(Ty.INTEGER))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__varargs_and_generated_sequence_flags__when__rendered__then__preserved(
    final boolean flag, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), new Hir.DynamicTy(Ty.INTEGER), flag, Ty.INTEGER);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[] { parameter }, flag, new Hir.DynamicTy(Ty.INFER));
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
  @ValueSource(strings = {
    "val f = (x: int) => x + 1; f(2)",
    "var a = [1, 2]; a[0] = 9; return a[0];"
  })
  void given__typed_source__when__rendered_repeatedly__then__stable_and_terminating(
    final String code, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var expression = Inf.codeToThir(code).root();
    final var actual = assertTimeout(Duration.ofSeconds(5), () -> printer.render(expression));
    printer.render(new Hir.Lexeme("unrelated"));
    assertAll(
      () -> assertEquals(actual, printer.render(expression)),
      () -> assertEquals(actual, new ToStringTreeHirVisitor().render(expression))
    );
    SnapshotTestUtils.assertMatches(testInfo, snapshot, code.startsWith("val") ? "function" : "array", actual);
  }

  @Test
  void given__resolved_declaration_cycle__when__rendered__then__symbolic_reference_without_following_target() {
    final var identifier = new Hir.Identifier("self", null);
    final var declaration = new Hir.Dec(new Hir.Lexeme(identifier.name()), Hir.MutabilityKind.IMMUTABLE, new Hir.DynamicTy(identifier));
    identifier.target(declaration);

    final var actual = assertTimeout(Duration.ofSeconds(1), () -> printer.render(declaration));
    assertEquals("""
      (Dec
        (mutability IMMUTABLE)
        (ty (named "VOID"))
        (resolvedTy null)
        (lexeme
          (Lexeme
            (name "self")
            (ty (named "VOID"))))
        (typeAnnotation
          (DynamicTy
            (ty (named "INFER"))
            (expression
              (Identifier
                (name "self")
                (target (reference "self")))))))""", actual);
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
      assertTimeout(Duration.ofSeconds(1), () -> printer.render(new Hir.DynamicTy(type))));
  }

  @Test
  void given__resolved_alias_constraint__when__rendered__then__cached_type_and_source_expression_are_both_visible() {
    final var source = new Hir.Identifier("Alias", null);
    assertEquals("""
      (DynamicTy
        (ty (boolean))
        (expression
          (Identifier
            (name "Alias")
            (target null))))""", printer.render(new Hir.DynamicTy(Ty.BOOLEAN, source)));
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
    assertAll(
      () -> assertTrue(binding.contains("(ty (named \"VOID\"))")),
      () -> assertTrue(binding.contains("(resolvedTy (number INTEGER")),
      () -> assertTrue(binding.contains("(typeAnnotation\n    (DynamicTy\n      (ty (named \"INFER\"))\n      (expression null)))")),
      () -> assertTrue(tuple.contains("(contextualType (struct (\"value\" (number INTEGER width=8")),
      () -> assertFalse(binding.contains("valueTy")),
      () -> assertFalse(tuple.contains("valueTy"))
    );
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
    final var declaration = new Hir.Dec(name, Hir.MutabilityKind.CONSTANT, new Hir.DynamicTy(Ty.INTEGER));
    final var initializer = new Hir.Assignment(name, value);
    final var array = new Hir.Array(new Hir.Expression[] { value }, new Hir.DynamicTy(Ty.INTEGER), value,
      Ty.VOID, new TyValueArray(Ty.INTEGER, 1));
    final var nodes = new Hir.Expression[] {
      array,
      new Hir.ArrayAccess(identifier, value, Ty.VOID, Ty.INTEGER),
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
      Hir.BuiltInTy.fromString("int", new MachineTarget(64))
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
      "(ty (named \"UNKNOWN\"))",
      "(ty (opaque))",
      "(ty (number INTEGER width=32 explicit=false signed=true radix=2 flags=[]))",
      "(ty (number FLOAT width=32 explicit=false signed=true radix=10 flags=[] precision=7))",
      "(ty (number DECIMAL width=128 explicit=false signed=true radix=10 flags=[] scale=10))",
      "(ty (boolean))",
      "(ty (string))",
      "(ty (array size=null (number INTEGER width=32 explicit=false signed=true radix=10 flags=[])))",
      "(ty (array size=0 (number INTEGER width=32 explicit=false signed=true radix=10 flags=[])))",
      "(ty (uninitialized (number INTEGER width=32 explicit=false signed=true radix=10 flags=[])))",
      "(ty (explicit-pointer CPU (number INTEGER width=32 explicit=false signed=true radix=10 flags=[])))",
      "(ty (union (number INTEGER width=32 explicit=false signed=true radix=10 flags=[]) (string)))",
      "(ty (function vararg=true (parameters (\"p\" (number INTEGER width=32 explicit=false signed=true radix=10 flags=[]))) (returns (named \"VOID\"))))",
      "(ty (struct (\"field\" (string))))"
    };
    for (int i = 0; i < types.length; i++) {
      assertEquals(expected[i], printer.render(types[i]));
    }
  }
}
