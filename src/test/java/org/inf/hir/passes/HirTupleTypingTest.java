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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

class HirTupleTypingTest {

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
    "val f = (t: (int, bool)): (int, bool) => t; f(t: (1, true))"
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
