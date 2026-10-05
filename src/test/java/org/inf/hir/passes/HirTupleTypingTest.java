package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

class HirTupleTypingTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "(a = 1, true, b = 2)",
    "val t: (a: int, bool, b: int) = (a = 1, true, b = 2); t",
    "var t = (a = 1, true, b = 2); t = (a = 3, false, b = 4); t",
    "val f = (t: (a: int, bool, b: int)): (a: int, bool, b: int) => t; f((a = 1, true, b = 2))",
    "val f = (): (a: int, bool, b: int) => { return (a = 1, true, b = 2); }; f()"
  })
  void given__named_and_mixed_tuple__when__typed__then__labels_and_source_order_are_preserved(final String code) {
    final var type = Assertions.assertInstanceOf(TyStruct.class, Inf.codeToThir(code).root().ty());
    final var expected = new TyStruct(new TyField[]{
      new TyField("a", Ty.INTEGER),
      new TyField(null, Ty.BOOLEAN),
      new TyField("b", Ty.INTEGER)
    }, true);
    Assertions.assertAll(
      () -> Assertions.assertTrue(type.tuple()),
      () -> Assertions.assertTrue(TypeComparison.sameValueType(type, expected))
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val t = (a = 1, true, b = 2); t.a | int",
    "val t = (a = 1, true, b = 2); t[1] | bool",
    "val t = (a = 1, true, b = 2); t.b | int",
    "val t = (a = 1, true, b = 2); t[2] | int",
    "val t = (outer = (inner = 2,),); t.outer.inner | int",
    "val t = (outer = (inner = 2,),); t[0][0] | int",
    "val t: (a: uint8,) = (a = 255,); t.a | uint8",
    "val t: (a: uint16,) = (a = 255u8,); t[0] | uint16"
  })
  void given__tuple_read__when__typed__then__named_and_positional_access_resolve_slots(final String code, final String type) {
    Assertions.assertTrue(TypeComparison.sameValueType(
      Inf.codeToThir(code).root().ty(), Tys.fromString(type, new MachineTarget(64))
    ));
  }

  private static TyStruct tuple(Ty... elements) {
    return new TyStruct(Arrays.stream(elements).map(type -> new TyField(null, type)).toArray(TyField[]::new));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(1, true)",
    "(1, true,)",
    "((1, true))",
    "val t = (1, true); t"
  })
  void given__positional_tuple__when__typed__then__ordered_heterogeneous_slots(String code) {
    Assertions.assertEquals(tuple(Ty.INTEGER, Ty.BOOLEAN), Inf.codeToThir(code).root().ty());
  }

  @Test
  void given__singleton_nested_tuples__when__typed__then__all_boundaries_preserved() {
    Assertions.assertEquals(
      tuple(tuple(Ty.INTEGER, Ty.BOOLEAN), tuple(Ty.INTEGER)),
      Inf.codeToThir("((1, true), (2,))").root().ty()
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (int, bool) = (1, true); t",
    "val f = (t: (int, bool)): (int, bool) => t; f((1, true))",
    "val f = (t: (int, bool)): (int, bool) => { return t; }; f((1, true))",
    "val f = (t: (int, bool)): (int, bool) => (1, true); val t = (1, true); f t",
    "var t: (int, bool) = (1, true); t = (2, false); t",
    "val f = (t: (int, bool)): (int, bool) => t; f(t = (1, true))"
  })
  void given__matching_annotations__when__typed__then__same_layout_metadata_is_accepted(String code) {
    final var intType = Tys.fromString("int", new MachineTarget(64));
    Assertions.assertEquals(tuple(intType, Ty.BOOLEAN), Inf.codeToThir(code).root().ty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (int,) = (1,); t",
    "val f = (t: (int,)): (int,) => (1,); f((2,))",
    "val t: ((int, bool), (int,)) = ((1, true), (2,)); t",
    "val f = (t: ((int, bool), (int,))): ((int, bool), (int,)) => t; val t = ((1, true), (2,)); f t",
    "val f = () => (1, true); val t: (int, bool) = f(); t",
    "val f = () => { return (1, true); }; val t: (int, bool) = f(); t",
    "val t = (1, true); val f = () => t; f()",
    "val n = 1; val f = (t: (int, bool)): (int, bool) => (n, true); f((2, false))",
    "val S = struct { val t: (int, bool); }; val s = new heap S { t = (1, true); }; s.t",
    "val S = struct { val x: int; }; val s = new heap S { x = 1; }; val t: (S, bool) = (s, true); t",
    "[(1, true), (2, false)][0]",
    "[(1, true); (int, bool); 1][0]",
    "val a: (int, bool) = (1, true); val b: (int, bool) = if (true) then a else (2, false); b",
    "val f = (flag: bool): (int, bool) => { val a: (int, bool) = (1, true); if (flag) { return a; } else { return (2, false); } }; f(true)"
  })
  void given__nested_and_inferred_signatures__when__typed__then__tuple_types_are_resolved(String code) {
    Assertions.assertInstanceOf(TyStruct.class, Inf.codeToThir(code).root().ty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = () => (1, { return true; }); f()",
    "val f = () => (1, { return true; }, { return 2; }); f()",
    "val f = () => ((1, { return true; }), 2); f()"
  })
  void given__nonreturning_element__when__typed__then__construction_is_deadend_and_later_returns_do_not_contribute(String code) {
    final var root = Inf.codeToThir(code).root();
    final List<Hir.Tuple> tuples = new ArrayList<>();
    root.visit(new HirVisitor() {
      @Override
      public void visitTuple(Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.BOOLEAN, root.ty()),
      () -> Assertions.assertEquals(false, tuples.isEmpty()),
      () -> Assertions.assertEquals(true, tuples.stream().allMatch(expression -> expression.ty() == Ty.DEADEND))
    );
  }

  @Test
  void given__side_effect_followed_by_value__when__typed__then__element_has_final_value_type() {
    Assertions.assertEquals(
      tuple(Ty.INTEGER, Ty.INTEGER),
      Inf.codeToThir("(10, { var x = 1; x = 2; 20 })").root().ty()
    );
  }

  static Stream<Hir.Expression> loopTransfers() {
    return Stream.of(
      new Hir.LoopBreak(null),
      new Hir.LoopContinue()
    );
  }

  @ParameterizedTest
  @MethodSource("loopTransfers")
  void given__loop_transfer_in_tuple__when__typed__then__transfer_is_preserved(Hir.Expression transfer) {
    final var expression = new Hir.Tuple(new Hir.TupleEntry[]{
      new Hir.TupleEntry(null, new Hir.Literal("1", Ty.INTEGER)),
      new Hir.TupleEntry(null, transfer)
    }, null);
    HirTyCommonVisitorPass.pass(expression);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.DEADEND, expression.ty()),
      () -> Assertions.assertEquals(Ty.DEADEND, expression.valueTy())
    );
  }

  @Test
  void given__tuple_inferred_and_declared_types__when__compared__then__explicit_width_metadata_only_differs() {
    final var actual = Inf.codeToThir("(1, true)").root().ty();
    final var expected = Inf.codeToThir("val t: (int, bool) = (1, true); t").root().ty();
    Assertions.assertAll(
      () -> Assertions.assertNotEquals(actual, expected),
      () -> Assertions.assertEquals(true, TypeComparison.sameValueType(actual, expected))
    );
  }
}
