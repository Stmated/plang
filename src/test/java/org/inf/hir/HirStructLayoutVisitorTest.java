package org.inf.hir;

import org.inf.Inf;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyStruct;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirStructLayoutVisitorTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val t: (value: int16,) = (value = 255u8,); t | false",
    "val use = () => ({ return true; }, 7); use() | true"
  })
  void given__source_tuple_context__when__layout_is_queried__then__completion_not_context_selects_the_layout(
    final String code, final boolean diverges
  ) {
    final var root = Inf.codeToThir(code).root();
    final var tuple = nodes(root, Hir.Tuple.class).getLast();
    final var completion = tuple.ty();
    final var contextual = tuple.contextualType();
    assertAll(
      () -> assertSame(diverges ? null : completion, Tys.getConstructionTargetTy(tuple)),
      () -> assertSame(completion, Tys.getIndexingReceiverTy(tuple)),
      () -> assertSame(contextual, tuple.contextualType())
    );
    if (diverges) {
      assertAll(
        () -> assertSame(Ty.DEADEND, completion),
        () -> assertNull(contextual)
      );
    } else {
      assertAll(
        () -> assertInstanceOf(TyStruct.class, completion),
        () -> assertNotNull(contextual),
        () -> assertEquals("value", assertInstanceOf(TyStruct.class, completion).fields()[0].name())
      );
    }
  }

  static Stream<Arguments> layoutTargets() {
    final var definitions = "val S = struct { val value: int; }; val s = new heap S { value = 7; }; ";
    return Stream.of(
      Arguments.of(definitions + "s", Hir.Identifier.class, true),
      Arguments.of(definitions + "S", Hir.Struct.class, true),
      Arguments.of(definitions + "s", Hir.Dec.class, true),
      Arguments.of(definitions + "val use = (item: S) => item; use", Hir.Parameter.class, true),
      Arguments.of(definitions + "{ s }", Hir.Block.class, true),
      Arguments.of(definitions + "{ return true; s; }", Hir.Block.class, true),
      Arguments.of(definitions + "{ return true; s; }", Hir.Expressions.class, true),
      Arguments.of(definitions + "val use = (item: S) => item; use(s)", Hir.Argument.class, true),
      Arguments.of(definitions + "(s,)", Hir.TupleEntry.class, true),
      Arguments.of("val S = struct { val value: int; }; var s = new heap S { value = 7; }; "
        + "s = s; s", Hir.Assignment.class, true),
      Arguments.of(definitions + "val make = (flag: bool) => s; make({ return true; })", Hir.Call.class, true),
      Arguments.of(definitions + "new heap S { value = { return true; }; }", Hir.NewByBlock.class, true),
      Arguments.of(definitions + "val holder = (inner = s,); holder.inner", Hir.DotAccess.class, true),
      Arguments.of(definitions + "[s][{ return true; }]", Hir.ArrayAccess.class, true),
      Arguments.of(definitions + "if ({ return true; }) then s else s", Hir.Conditional.class, true),
      Arguments.of(definitions + "return s", Hir.Return.class, false),
      Arguments.of(definitions + "for (var i = 0; i < 1; i += 1) { s; }", Hir.LoopBreak.class, false),
      Arguments.of(definitions + "{ return s; }", Hir.Block.class, false),
      Arguments.of(definitions + "{ val ignored = false; val n = 7; }", Hir.Expressions.class, false),
      Arguments.of(definitions + "[s]", Hir.Array.class, false),
      Arguments.of(definitions + "if (true) then s else false", Hir.Conditional.class, false),
      Arguments.of(definitions + "val make = () => s; make", Hir.Function.class, false),
      Arguments.of("7", Hir.Literal.class, false),
      Arguments.of("({ return true; }, 7)", Hir.Tuple.class, false),
      Arguments.of("val t: (int16,) = (255u8,); t", Hir.Convert.class, false)
    );
  }

  @ParameterizedTest
  @MethodSource("layoutTargets")
  void given__typed_source_target__when__layout_is_queried__then__only_existing_layouts_are_exposed_without_mutation(
    final String code, final Class<? extends Hir.Expression> nodeType, final boolean hasLayout
  ) {
    final var root = Inf.codeToThir(code).root();
    final var expression = nodes(root, nodeType).getLast();
    final var expected = hasLayout ? declaration(root, "S").resolvedTy() : null;
    final var completion = expression.ty();
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    assertAll(
      () -> assertEquals(expected, HirStructLayoutVisitor.find(expression)),
      () -> assertEquals(expected, Tys.getConstructionTargetTy(expression)),
      () -> assertEquals(expected, Tys.getMemberReceiverTy(expression)),
      () -> assertSame(completion, expression.ty()),
      () -> assertEquals(before, printer.render(root))
    );
  }

  static Stream<Arguments> incompleteTargets() {
    return Stream.of(
      Arguments.of("missing", Hir.Identifier.class),
      Arguments.of("val s: Named; s", Hir.Dec.class),
      Arguments.of("val use = (item: Named) => item; use", Hir.Parameter.class),
      Arguments.of("val make = () => missing; make()", Hir.Call.class)
    );
  }

  @ParameterizedTest
  @MethodSource("incompleteTargets")
  void given__untyped_source_target__when__layout_is_queried__then__no_layout_is_inferred(
    final String code, final Class<? extends Hir.Expression> nodeType
  ) {
    final var root = Inf.codeToHir(code);
    final var expression = nodes(root, nodeType).getLast();
    final var completion = expression.ty();
    assertAll(
      () -> assertNull(HirStructLayoutVisitor.find(expression)),
      () -> assertNull(Tys.getConstructionTargetTy(expression)),
      () -> assertNull(Tys.getMemberReceiverTy(expression)),
      () -> assertSame(completion, expression.ty())
    );
  }

  @Test
  void given__source_noncontinuing_construction__when__named_layout_metadata_is_refined__then__queries_read_the_latest_target() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: int; val flag: bool; };
      new heap S { value = 7; flag = { return true; }; }
      """).root();
    final var creation = nodes(root, Hir.NewByBlock.class).getLast();
    final var definition = nodes(root, Hir.Struct.class).getFirst();
    final var namedType = declaration(root, "S");
    final var original = definition.ty();
    final var refined = nodes(Inf.codeToThir(
      "val S = struct { val value: bool; val flag: bool; }; S").root(), Hir.Struct.class).getFirst().ty();
    assertAll(
      () -> assertSame(original, Tys.getConstructionTargetTy(creation)),
      () -> assertSame(Ty.DEADEND, creation.ty())
    );

    definition.ty(refined);
    namedType.resolvedTy(refined);

    assertAll(
      () -> assertNotEquals(original, refined),
      () -> assertSame(refined, Tys.getConstructionTargetTy(creation)),
      () -> assertSame(refined, Tys.getMemberReceiverTy(creation)),
      () -> assertSame(Ty.DEADEND, creation.ty())
    );
  }

  @Test
  void given__source_factory_call__when__signature_metadata_is_refined__then__layout_query_reads_the_latest_signature() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: int; };
      val make = (flag: bool) => new heap S { value = 7; };
      make({ return true; })
      """).root();
    final var function = nodes(root, Hir.Function.class).getFirst();
    final var call = nodes(root, Hir.Call.class).getLast();
    final var original = function.ty().returnTy();
    final var refined = nodes(Inf.codeToThir(
      "val S = struct { val value: bool; }; S").root(), Hir.Struct.class).getFirst().ty();
    assertSame(original, HirStructLayoutVisitor.find(call));

    function.signature().ty(function.ty().toBuilder().returnTy(refined).build());
    declaration(root, "make").resolvedTy(function.ty());

    assertAll(
      () -> assertSame(refined, HirStructLayoutVisitor.find(call)),
      () -> assertSame(Ty.DEADEND, call.ty()),
      () -> assertSame(Ty.INFER, function.signature().returnTypeAnnotation().ty())
    );
  }

  @Test
  void given__source_conditional_receiver__when__named_layout_metadata_is_refined__then__queries_read_the_latest_branches() {
    final var root = Inf.codeToThir("""
      val S = struct { val value: int; };
      if ({ return true; }) then new heap S { value = 7; } else new heap S { value = 8; }
      """).root();
    final var conditional = nodes(root, Hir.Conditional.class).getFirst();
    final var definition = nodes(root, Hir.Struct.class).getFirst();
    final var original = definition.ty();
    final var refined = nodes(Inf.codeToThir(
      "val S = struct { val value: bool; }; S").root(), Hir.Struct.class).getFirst().ty();
    assertSame(original, Tys.getMemberReceiverTy(conditional));

    definition.ty(refined);
    declaration(root, "S").resolvedTy(refined);

    assertAll(
      () -> assertSame(refined, Tys.getMemberReceiverTy(conditional)),
      () -> assertSame(refined, Tys.getConstructionTargetTy(conditional)),
      () -> assertSame(Ty.DEADEND, conditional.ty())
    );
  }

  @Test
  void given__source_struct_parameter_reference__when__queried__then__the_actual_parameter_binding_is_used() {
    final var root = Inf.codeToThir(
      "val S = struct { val value: int; }; val read = (item: S) => item; read").root();
    final var parameter = nodes(root, Hir.Parameter.class).getFirst();
    final var reference = nodes(root, Hir.Identifier.class).stream()
      .filter(identifier -> identifier.target() == parameter).findFirst().orElseThrow();
    assertAll(
      () -> assertSame(parameter, reference.target()),
      () -> assertSame(parameter.resolvedTy(), HirStructLayoutVisitor.find(parameter)),
      () -> assertSame(parameter.resolvedTy(), HirStructLayoutVisitor.find(reference))
    );
  }

  private static Hir.Dec declaration(final Hir.Expression root, final String name) {
    return nodes(root, Hir.Dec.class).stream().filter(candidate -> candidate.lexeme().name().equals(name))
      .findFirst().orElseThrow();
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
