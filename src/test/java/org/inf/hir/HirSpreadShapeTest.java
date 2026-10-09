package org.inf.hir;

import org.inf.Inf;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyStruct;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HirSpreadShapeTest {

  static Stream<String> resolvedOperands() {
    final var definitions = "val args = (b = true, a = 7); val consume = (a: int, b: bool) => a; ";
    return Stream.of(
      definitions + "consume(...(b = true, a = 7))",
      definitions + "consume(...args)",
      definitions + "val use = (items: (b: bool, a: int)) => consume(...items); use((b = true, a = 7))",
      definitions + "consume(...({ return true; args; }))",
      definitions + "val make = (flag: bool) => args; consume(...(make({ return true; })))",
      definitions + "val S = struct { val b: bool; val a: int; }; "
        + "consume(...(new heap S { b = true; a = { return true; }; }))",
      definitions + "val holder = (items = args,); consume(...holder.items)",
      definitions + "val values = [args]; consume(...values[{ return true; }])",
      definitions + "consume(...(if ({ return true; }) then args else args))"
    );
  }

  @ParameterizedTest
  @MethodSource("resolvedOperands")
  void given__source_spread_operand__when__fields_are_queried__then__existing_named_layout_is_read_without_mutation(
    final String code
  ) {
    final var root = Inf.codeToThir(code).root();
    final var spread = nodes(root, Hir.Spread.class).getFirst();
    final var completion = spread.ty();
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    final var fields = HirSpreadShape.fields(spread);
    assertAll(
      () -> assertSame(fields, HirSpreadShape.availableFields(spread)),
      () -> assertEquals(2, fields.length),
      () -> assertEquals("b", fields[0].name()),
      () -> assertEquals("a", fields[1].name()),
      () -> assertTrue(TypeComparison.sameValueType(Ty.BOOLEAN, fields[0].ty())),
      () -> assertTrue(TypeComparison.sameValueType(Ty.INTEGER, fields[1].ty())),
      () -> assertSame(completion, spread.ty()),
      () -> assertEquals(before, printer.render(root))
    );
  }

  @Test
  void given__source_factory_spread__when__return_metadata_is_refined__then__binding_uses_current_names_and_arity() {
    final var root = Inf.codeToThir("""
      val make = (flag: bool) => (true,);
      val consume = (flag: bool) => flag;
      consume(...(make({ return true; })))
      """).root();
    final var factory = nodes(root, Hir.Function.class).getFirst();
    final var spread = nodes(root, Hir.Spread.class).getFirst();
    final var argument = nodes(root, Hir.Argument.class).stream()
      .filter(candidate -> candidate.value() == spread).findFirst().orElseThrow();
    final var original = assertInstanceOf(TyStruct.class, factory.ty().returnTy());
    final var refined = assertInstanceOf(TyStruct.class,
      nodes(Inf.codeToThir("(b = true, a = 7)").root(), Hir.Tuple.class).getFirst().ty());
    assertSame(original.fields(), HirSpreadShape.fields(spread));

    factory.signature().ty(factory.ty().toBuilder().returnTy(refined).build());
    nodes(root, Hir.Dec.class).stream().filter(declaration -> declaration.lexeme().name().equals("make"))
      .findFirst().orElseThrow().resolvedTy(factory.ty());

    assertAll(
      () -> assertSame(refined.fields(), HirSpreadShape.fields(spread)),
      () -> assertEquals(2, HirCallArguments.count(new Hir.Argument[]{argument})),
      () -> assertArrayEquals(new int[]{1, 0}, HirCallArguments.bind(
        argument, new HirArgumentBinding(new String[]{"a", "b"}, false, 2)
      )),
      () -> assertSame(Ty.DEADEND, spread.ty()),
      () -> assertSame(Ty.INFER, factory.signature().returnTypeAnnotation().ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "args",
    "s",
    "make()",
    "(b = 2, a = 1)",
    "{ args }",
    "holder.args",
    "values[0]"
  })
  void given__tuple_or_struct_spread__when__typed__then__source_field_order_and_names_are_preserved(final String operand) {
    final var root = Inf.codeToThir("""
      val S = struct { val b: uint8; val a: uint8; };
      val s = new heap S { b = 2; a = 1; };
      val make = (): S => s;
      val args = (b = 2u8, a = 1u8);
      val holder = (args = args,);
      val values = [args];
      val pair = (a: uint8, b: uint8) => a;
      pair(...(%s))
      """.formatted(operand)).root();
    final var spreads = nodes(root, Hir.Spread.class);
    assertEquals(1, spreads.size());
    final var fields = HirSpreadShape.fields(spreads.getFirst());
    assertAll(
      () -> assertEquals(2, fields.length),
      () -> assertEquals("b", fields[0].name()),
      () -> assertEquals("a", fields[1].name()),
      () -> assertTrue(TypeComparison.sameValueType(root.ty(), fields[0].ty())),
      () -> assertTrue(TypeComparison.sameValueType(root.ty(), fields[1].ty()))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "({ return true; },)",
    "(1, { return true; })",
    "({ return true; }, 2)"
  })
  void given__diverging_tuple_construction__when__spread_is_typed__then__no_layout_or_slots_are_manufactured(
    final String operand
  ) {
    final var root = Inf.codeToThir("""
      val pair = (a: uint8, b: uint8) => a;
      val use = () => pair(...(%s));
      use()
      """.formatted(operand)).root();
    final var spreads = nodes(root, Hir.Spread.class);
    assertEquals(1, spreads.size());
    final var spread = spreads.getFirst();
    final var tuple = assertInstanceOf(Hir.Tuple.class, spread.value());
    assertAll(
      () -> assertEquals(Ty.DEADEND, tuple.ty()),
      () -> assertNull(tuple.contextualType()),
      () -> assertEquals(0, HirSpreadShape.fields(spread).length),
      () -> assertEquals(Ty.BOOLEAN, root.ty())
    );
  }

  @Test
  void given__source_spread_with_only_a_return__when__queried__then__no_nominal_layout_or_slots_are_manufactured() {
    final var root = Inf.codeToThir("val use = (value: int) => value; use(...({ return true; }))").root();
    final var spread = nodes(root, Hir.Spread.class).getFirst();
    assertAll(
      () -> assertSame(Ty.DEADEND, spread.ty()),
      () -> assertEquals(0, HirSpreadShape.fields(spread).length),
      () -> assertEquals(0, HirSpreadShape.availableFields(spread).length)
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "consume(...(1))",
    "consume(...[1, 2])",
    "consume(...(if (true) then (1,) else false))",
    "consume(...unknown)",
    "val use = (value) => consume(...value); use"
  })
  void given__incomplete_source_spread_without_a_layout__when__queried__then__preparation_defers_and_strict_resolution_rejects(
    final String code
  ) {
    final var spread = nodes(Inf.codeToHir(code), Hir.Spread.class).getFirst();
    final var completion = spread.ty();
    assertNull(HirSpreadShape.availableFields(spread));
    final var error = assertThrows(IllegalArgumentException.class, () -> HirSpreadShape.fields(spread));
    assertAll(
      () -> assertTrue(error.getMessage().startsWith("Spreading requires a statically known tuple or struct")),
      () -> assertSame(completion, spread.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val consume = (value: int) => value; consume(...(1))",
    "val consume = (value: int) => value; consume(...[1, 2])",
    "val consume = (value: int) => value; consume(...(if (true) then (1,) else false))",
    "val consume = (value: int) => value; val fn = () => 7; consume(...fn)"
  })
  void given__typed_source_spread_without_a_static_layout__when__pipeline_runs__then__nonaggregate_or_union_layout_is_rejected(
    final String code
  ) {
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    assertTrue(error.getMessage().startsWith("Spreading requires a statically known tuple or struct"));
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
