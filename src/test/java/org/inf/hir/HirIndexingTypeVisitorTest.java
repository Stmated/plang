package org.inf.hir;

import org.inf.Inf;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirIndexingTypeVisitorTest {

  static Stream<Arguments> indexingInputs() {
    return Stream.of(
      Arguments.of("val a = [7]; a", Hir.Identifier.class, "a"),
      Arguments.of("val a = [7]; a", Hir.Dec.class, "a"),
      Arguments.of("val read = (a: [;int;1]) => a; read", Hir.Parameter.class, "a"),
      Arguments.of("val a = [7]; { a }", Hir.Block.class, "a"),
      Arguments.of("val a = [7]; { return true; a; }", Hir.Block.class, "a"),
      Arguments.of("val a = [7]; { return true; a; }", Hir.Expressions.class, "a"),
      Arguments.of("val a = [7i32]; val read = (value: [;int;1]) => value; read(a)", Hir.Argument.class, "a"),
      Arguments.of("val a = [7i32]; val take = (value: [;int;1]) => 0; take({ return true; a; })",
        Hir.Argument.class, "a"),
      Arguments.of("val a = [7]; (a,)", Hir.TupleEntry.class, "a"),
      Arguments.of("var a = [7]; a = [8]; a", Hir.Assignment.class, "a"),
      Arguments.of("val a = [7]; val make = (flag: bool) => a; make({ return true; })", Hir.Call.class, "a"),
      Arguments.of("val a = [7]; val holder = (items = a,); holder.items", Hir.DotAccess.class, "a"),
      Arguments.of("val a = [7]; [a][{ return true; }]", Hir.ArrayAccess.class, "a"),
      Arguments.of("val a = [7]; if ({ return true; }) then a else a", Hir.Conditional.class, "a"),
      Arguments.of("[{ return true; }, 7]", Hir.Array.class, "metadata"),
      Arguments.of("(7,)", Hir.Tuple.class, "completion"),
      Arguments.of("({ return true; }, 7)", Hir.Tuple.class, "completion"),
      Arguments.of("val a = [7]; return a", Hir.Return.class, "completion"),
      Arguments.of("for (var i = 0; i < 1; i += 1) { i; }", Hir.LoopBreak.class, "completion"),
      Arguments.of("{ return true; }", Hir.Block.class, "completion"),
      Arguments.of("{ val ignored = false; val n = 7; }", Hir.Expressions.class, "n"),
      Arguments.of("7", Hir.Literal.class, "completion")
    );
  }

  @ParameterizedTest
  @MethodSource("indexingInputs")
  void given__typed_source_input__when__indexing_queries_run__then__resolved_metadata_is_read_without_mutation(
    final String code, final Class<? extends Hir.Expression> nodeType, final String expectedSource
  ) {
    final var root = Inf.codeToThir(code).root();
    final var expression = nodes(root, nodeType).getLast();
    final Ty expected = switch (expectedSource) {
      case "completion" -> expression.ty();
      case "metadata" -> switch (expression) {
        case Hir.Array array -> array.arrayTy();
        default -> throw new AssertionError("Expected named indexing metadata");
      };
      default -> binding(root, expectedSource);
    };
    final var completion = expression.ty();
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    assertAll(
      () -> assertEquals(expected, HirIndexingTypeVisitor.find(expression)),
      () -> assertEquals(expected, Tys.getIndexingReceiverTy(expression)),
      () -> assertEquals(expected, Tys.getIndexingAccessorTy(expression)),
      () -> assertSame(completion, expression.ty()),
      () -> assertEquals(before, printer.render(root))
    );
  }

  static Stream<Arguments> incompleteInputs() {
    return Stream.of(
      Arguments.of("val a: [;int;1]; a", Hir.Dec.class),
      Arguments.of("val read = (a: [;int;1]) => a; read", Hir.Parameter.class),
      Arguments.of("missing", Hir.Identifier.class),
      Arguments.of("val make = () => [7]; make()", Hir.Call.class)
    );
  }

  @ParameterizedTest
  @MethodSource("incompleteInputs")
  void given__untyped_source_binding_or_call__when__queried__then__no_type_is_inferred(
    final String code, final Class<? extends Hir.Expression> nodeType
  ) {
    final var root = Inf.codeToHir(code);
    final var expression = nodes(root, nodeType).getLast();
    final var completion = expression.ty();
    assertAll(
      () -> assertNull(HirIndexingTypeVisitor.find(expression)),
      () -> assertNull(Tys.getIndexingReceiverTy(expression)),
      () -> assertNull(Tys.getIndexingAccessorTy(expression)),
      () -> assertSame(completion, expression.ty())
    );
  }

  @Test
  void given__source_parameter_reference__when__queried__then__the_actual_parameter_binding_is_used() {
    final var root = Inf.codeToThir("val read = (items: [;int;1]) => items; read").root();
    final var parameter = nodes(root, Hir.Parameter.class).getFirst();
    final var reference = nodes(root, Hir.Identifier.class).stream()
      .filter(identifier -> identifier.target() == parameter).findFirst().orElseThrow();
    assertAll(
      () -> assertSame(parameter, reference.target()),
      () -> assertSame(parameter.resolvedTy(), HirIndexingTypeVisitor.find(parameter)),
      () -> assertSame(parameter.resolvedTy(), HirIndexingTypeVisitor.find(reference))
    );
  }

  @Test
  void given__source_factory_call__when__its_body_is_retyped__then__queries_read_the_latest_signature() {
    final var root = Inf.codeToThir("val make = (flag: bool) => [7]; make({ return true; })").root();
    final var function = nodes(root, Hir.Function.class).getFirst();
    final var call = nodes(root, Hir.Call.class).getLast();
    final var original = function.signature().ty().returnTy();
    final var replacement = nodes(Inf.codeToThir("val make = (flag: bool) => [true, false]; make").root(),
      Hir.Function.class).getFirst();
    assertSame(original, HirIndexingTypeVisitor.find(call));

    function.body(replacement.body());
    HirTyCommonVisitorPass.pass(root);

    final var refined = function.signature().ty().returnTy();
    assertAll(
      () -> assertNotEquals(original, refined),
      () -> assertSame(refined, Tys.getIndexingReceiverTy(call)),
      () -> assertSame(refined, Tys.getIndexingAccessorTy(call)),
      () -> assertSame(Ty.DEADEND, call.ty()),
      () -> assertSame(Ty.INFER, function.signature().returnTypeAnnotation().ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "[7][{ return true; }]",
    "[[7]][{ return true; }]",
    "val S = struct { val value: int; }; [new heap S { value = 7; }][{ return true; }]",
    "val f = () => 7; [f][{ return true; }]"
  })
  void given__source_indexed_value__when__context_queries_run__then__the_resolved_selection_is_read(final String code) {
    final var access = nodes(Inf.codeToThir(code).root(), Hir.ArrayAccess.class).getLast();
    final var selected = access.indexedTy();
    assertAll(
      () -> assertSame(Ty.DEADEND, access.ty()),
      () -> assertSame(selected, Tys.getIndexedTy(access)),
      () -> assertSame(selected, Tys.getBindingTy(access)),
      () -> assertSame(selected, Tys.getAssignmentContextTy(access)),
      () -> assertSame(selected, Tys.getIndexingReceiverTy(access)),
      () -> assertSame(selected, Tys.getIndexingAccessorTy(access)),
      () -> assertSame(selected instanceof TyStruct ? selected : null, Tys.getMemberReceiverTy(access)),
      () -> assertSame(selected instanceof TyStruct ? selected : null, Tys.getConstructionTargetTy(access)),
      () -> assertSame(selected instanceof TyFn ? selected : null, Tys.getCallableSignature(access))
    );
  }

  @Test
  void given__source_conditional_receiver__when__array_elements_are_retyped__then__queries_read_the_latest_branches() {
    final var root = Inf.codeToThir("if ({ return true; }) then [7] else [7]").root();
    final var conditional = nodes(root, Hir.Conditional.class).getFirst();
    final var arrays = nodes(root, Hir.Array.class);
    final var original = arrays.getFirst().arrayTy();
    final var replacement = nodes(Inf.codeToThir("[true, false]").root(), Hir.Array.class).getFirst();
    assertEquals(original, Tys.getIndexingReceiverTy(conditional));

    for (final var array : arrays) {
      array.elements(replacement.elements());
      array.length(replacement.length());
    }
    HirTyCommonVisitorPass.pass(root);

    final var refined = arrays.getFirst().arrayTy();
    assertAll(
      () -> assertNotEquals(original, refined),
      () -> assertEquals(refined, Tys.getIndexingReceiverTy(conditional)),
      () -> assertEquals(refined, Tys.getIndexingAccessorTy(conditional)),
      () -> assertSame(Ty.DEADEND, conditional.ty())
    );
  }

  @Test
  void given__source_slot_conversion__when__queried__then__destination_type_not_operand_type_is_read() {
    final var conversion = nodes(Inf.codeToThir("val t: (int16,) = (255u8,); t").root(), Hir.Convert.class).getFirst();
    assertAll(
      () -> assertNotEquals(conversion.expression().ty(), conversion.targetTy()),
      () -> assertSame(conversion.targetTy(), HirIndexingTypeVisitor.find(conversion)),
      () -> assertSame(conversion.targetTy(), Tys.getIndexingAccessorTy(conversion))
    );
  }

  private static Ty binding(final Hir.Expression root, final String name) {
    for (final var declaration : nodes(root, Hir.Dec.class)) {
      if (declaration.lexeme().name().equals(name)) {
        return declaration.resolvedTy();
      }
    }
    return nodes(root, Hir.Parameter.class).stream()
      .filter(parameter -> parameter.lexeme().name().equals(name)).findFirst().orElseThrow().resolvedTy();
  }

  private static <T extends Hir.Expression> List<T> nodes(final Hir.Expression root, final Class<T> nodeType) {
    final var found = new ArrayList<T>();
    root.visit(new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        if (nodeType.isInstance(expression)) {
          found.add(nodeType.cast(expression));
        }
        HirVisitor.super.visitChild(expression);
      }
    });
    assertFalse(found.isEmpty(), () -> "Missing source node: " + nodeType.getSimpleName());
    return found;
  }
}
