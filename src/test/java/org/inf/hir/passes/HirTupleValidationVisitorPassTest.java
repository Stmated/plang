package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.IdentityHashMap;

class HirTupleValidationVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "var t = (1, true); t[0] = 2",
    "var t = (1, true); t[0] += 2",
    "var t = ((1,), true); t[0][0] = 2",
    "var t = ((1,), true); t[0] = (2,)",
    "val f = () => (1, { return true; }, { var t = (1,); t[0] = 2; 3 }); f()",
    "val S = struct { val t: (int, bool); }; val s = new heap S { t = (1, true); }; s.t[0] = 2",
    "val S = struct { val t: (int, bool); }; val s = new heap S { t = (1, true); }; s.t[0] += 2"
  })
  void given__tuple_slot_write__when__typed__then__explicitly_rejected(String code) {
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    Assertions.assertEquals("Tuple element writes are not supported yet", error.getMessage());
  }

  private static Hir.Expression infer(String code) {
    final var hir = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(hir, Hir.Identifier::target);
    return HirTyCommonVisitorPass.pass(hir);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (1, true) = (1, true)",
    "val f = (t: (1, true)) => t",
    "val f = (): (1, true) => (1, true)",
    "[(1, true); (1, true); 1]",
    "val t: ((int, 1), bool) = ((1, 2), true)",
    "val f = () => 1; val t: (f(), bool) = (1, true)",
    "val f = (): (int, { return true; }) => (1, true)",
    "val t: ([1], bool) = ([1], true)",
    "val x = 1; val t: ([;x;1], bool) = ([1], true)"
  })
  void given__value_expression_in_tuple_annotation__when__validated_after_inference__then__rejected(String code) {
    final var hir = infer(code);
    final var error = Assertions.assertThrows(
      IllegalArgumentException.class, () -> HirTupleValidationVisitorPass.pass(hir)
    );
    Assertions.assertEquals("Tuple annotations require types, not value expressions", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: ((int, bool), int) = ((1, true), 2); t",
    "val f = (t: (int, bool)): (int, bool) => (2, false); f((1, true))",
    "[(1, true); (int, bool); 1]",
    "val S = struct { val x: int; }; val s = new heap S { x = 1; }; val t: (S, bool) = (s, true); t",
    "val f = () => (1, { return true; }); f()",
    "val t = ({ val x: (int, bool) = (1, true); x }, 2); t",
    "val t: ([;int;1], bool) = ([1], true); t",
    "val t: ([;[;int;1];1],) = ([[1]],); t",
    "val t: ([;(int, bool);1],) = ([(1, true)],); t"
  })
  void given__typed_tuples__when__validated__then__type_references_are_preserved(String code) {
    final var hir = infer(code);
    final var types = new IdentityHashMap<Hir.Tuple, Ty>();
    final var valueTypes = new IdentityHashMap<Hir.Tuple, Ty>();
    hir.visit(new HirVisitor() {
      @Override
      public void visitTuple(Hir.Tuple expression) {
        types.put(expression, expression.ty());
        valueTypes.put(expression, expression.valueTy());
        HirVisitor.super.visitTuple(expression);
      }
    });

    HirTupleValidationVisitorPass.pass(hir);

    Assertions.assertEquals(false, types.isEmpty());
    types.forEach((tuple, type) -> Assertions.assertAll(
      () -> Assertions.assertSame(type, tuple.ty()),
      () -> Assertions.assertSame(valueTypes.get(tuple), tuple.valueTy())
    ));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (int, bool) = (1, 2)",
    "val t: (int,) = (1, true)",
    "val t: (int, bool) = ((1,), true)",
    "val t: int = (1,)",
    "val t: (int,) = 1",
    "var t = (1, true); t = (false, 2)",
    "val f = (t: (int, bool)) => 1; f((1, 2))",
    "val f = (t: (int, bool)) => 1; f((1,))",
    "val f = (t: int) => 1; f((1,))",
    "val f = (): (int, bool) => (1, 2)",
    "val f = (): (int, bool) => { return (1, 2); }",
    "val f = (): int => (1,)",
    "val f = (): (int,) => 1",
    "val f = (t: (int, bool)) => 1; f(t: (1, 2))",
    "val f = (): (int, bool) => { return (1, true); return (1, 2); }",
    "val S = struct { val t: (int, bool); }; new heap S { t = (1, 2); }",
    "val f = (t: ((int, bool),)) => 1; val t = ((1, 2),); f t",
    "val captured = 1; val f = (t: (int, bool)) => captured; val t = (1, 2); f t",
    "val values = [(1, true)]; values[0] = (2, 3)",
    "val values = [(1, true), (2, 3)]",
    "val S = struct { val t: (int, bool); }; val s = new heap S { t = (1, true); }; s.t = (2, 3)",
    "val f = (): ((int, bool),) => ((1, 2),)",
    "val t: (uint8, uint8) = (256, 2)",
    "val f = (t: (uint32, bool)) => 1; val x = 1; f((x, true))"
  })
  void given__incompatible_tuple_boundary__when__typed__then__explicit_conversion_error(String code) {
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
    Assertions.assertEquals(true, error.getMessage().contains("tuple"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = (t: (int, bool)) => 1; f(1, true)",
    "val f = (t: (int, bool)) => 1; f()",
    "val f = (t: (int, bool)) => 1; f((1, true), (2, false))",
    "val f = (t: (int, bool)) => 1; f(other: (1, true))",
    "val f = (t: (int, bool)) => 1; f(t: (1, true), t: (2, false))",
    "val f = (t: (int, bool), x: int) => x; val t = (1, true); f t",
    "val captured = 1; val f = (t: (int, bool)) => captured; f(1, true)",
    "val f = (t: (int, bool)) => 1; val t = (1, true); f t, t"
  })
  void given__incorrect_tuple_call_arguments__when__typed__then__rejected(String code) {
    Assertions.assertThrows(RuntimeException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(1, ())",
    "(1, { var x = 2; })",
    "val log = () => (); (1, log())",
    "val f = () => (1, { return true; }, ()); f()",
    "val f = () => (1, { return true; }, missing); f()",
    "val t: (1, 2) = (1, 2)",
    "val x = 1; val t: (x, bool) = (1, true)",
    "val t: (int, ()) = (1, 2)",
    "[(1, true); (1, true); 1]",
    "(label: 1)",
    "(label: 1,)",
    "(label: 1, 2)",
    "val t: (label: int, bool) = (1, true)"
  })
  void given__invalid_tuple_elements_or_annotations__when__typed__then__rejected(String code) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
  }
}
