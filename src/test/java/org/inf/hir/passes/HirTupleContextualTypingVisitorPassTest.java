package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirTupleContextualTypingVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (p1: uint8, p2: uint8) = (1, 2); t",
    "val t: (p1: uint8, p2: uint8) = (p1 = 1, p2 = 2); t",
    "val t: (p1: uint8, p2: uint8) = (p2 = 2, p1 = 1); t",
    "val t: (p1: uint8, p2: uint8) = (2, p1 = 1); t",
    "var t: (p1: uint8, p2: uint8) = (1, 2); t = (p2 = 4, 3); t",
    "val fn4 = (t: (p1: uint8, p2: uint8)) => t; fn4((1, 2))",
    "val fn4 = (t: (p1: uint8, p2: uint8)) => t; fn4((p1 = 1, p2 = 2))",
    "val fn4 = (t: (p1: uint8, p2: uint8)) => t; fn4 ((p2 = 2, 1))",
    "val fn4 = (x: int, t: (p1: uint8, p2: uint8)) => t; fn4(t: (2, p1 = 1), x: 3)",
    "val f = (): (p1: uint8, p2: uint8) => (2, p1 = 1); f()",
    "val f = (): (p1: uint8, p2: uint8) => { return (p2 = 2, p1 = 1); }; f()",
    "val t: (p1: uint8, p2: uint8) = { (p2 = 2, 1) }; t",
    "val t: (p1: uint8, p2: uint8) = if (true) then (1, 2) else { (2, p1 = 1) }; t"
  })
  void given__fresh_tuple_context__when__matched__then__destination_labels_and_integer_types_are_used(final String code) {
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    final var expected = new TyStruct(new TyField[]{
      new TyField("p1", uint8),
      new TyField("p2", uint8)
    }, true);
    Assertions.assertTrue(TypeComparison.sameValueType(Inf.codeToThir(code).root().ty(), expected));
  }

  @Test
  void given__reordered_context__when__inference_is_repeated__then__source_entries_and_destination_layout_are_preserved() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val t: (a: uint8, bool, b: uint16) = (true, b = 255u8, a = 1); t"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTupleContextualTypingVisitorPass.pass(root);
    final var expected = root.ty();
    HirTyCommonVisitorPass.pass(root);
    HirTupleValidationVisitorPass.pass(root);
    HirTupleContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    HirTupleValidationVisitorPass.pass(root);
    final var constructions = new ArrayList<Hir.Tuple>();
    final var conversions = new ArrayList<Hir.Convert>();
    root.visit(new HirVisitor() {
      @Override
      public void visitTuple(final Hir.Tuple tuple) {
        if (tuple.contextualType() != null) {
          constructions.add(tuple);
        }
        HirVisitor.super.visitTuple(tuple);
      }

      @Override
      public void visitConvert(final Hir.Convert conversion) {
        conversions.add(conversion);
        HirVisitor.super.visitConvert(conversion);
      }
    });
    Assertions.assertEquals(1, constructions.size());
    final var tuple = constructions.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, root.ty()),
      () -> Assertions.assertTrue(TypeComparison.sameValueType(tuple.ty(), tuple.contextualType())),
      () -> Assertions.assertNull(tuple.children()[0].label()),
      () -> Assertions.assertEquals("b", tuple.children()[1].label().name()),
      () -> Assertions.assertEquals("a", tuple.children()[2].label().name()),
      () -> Assertions.assertEquals(1, conversions.size()),
      () -> Assertions.assertEquals(Tys.fromString("uint16", new MachineTarget(64)), conversions.getFirst().ty())
    );
  }

  @Test
  void given__standalone_tuple_pass__when__followed_by_common_inference__then__explicit_widening_is_preserved() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val t: (int16, (uint8,)) = (255u8, (1,)); t"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTupleContextualTypingVisitorPass.pass(root);
    final var expected = root.ty();

    HirTyCommonVisitorPass.pass(root);
    HirTupleValidationVisitorPass.pass(root);
    HirTupleContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    HirTupleValidationVisitorPass.pass(root);

    final var conversions = new ArrayList<Hir.Convert>();
    root.visit(new HirVisitor() {
      @Override
      public void visitConvert(Hir.Convert conversion) {
        conversions.add(conversion);
        HirVisitor.super.visitConvert(conversion);
      }
    });
    Assertions.assertEquals(1, conversions.size());
    final var conversion = conversions.getFirst();
    final var source = Assertions.assertInstanceOf(Hir.Literal.class, conversion.expression());
    final var sourceType = Assertions.assertInstanceOf(TyValueNumberInteger.class, source.ty());
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, root.ty()),
      () -> Assertions.assertEquals(Tys.fromString("int16", new MachineTarget(64)), conversion.ty()),
      () -> Assertions.assertEquals(8, sourceType.width().value()),
      () -> Assertions.assertEquals("255u8", source.content())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (uint8, uint8) = (1, 2); t",
    "var t: (uint8, uint8) = (1, 2); t = (3, 4); t",
    "var t = (1u8, 2u8); t = (3, 4); t",
    "val t: (uint8, uint8) = { val x = 99; (1, 2) }; t",
    "val t: (uint8, uint8) = if (true) then (1, 2) else { (3, 4) }; t",
    "val f = (t: (uint8, uint8)) => t; f((1, 2))",
    "val f = (t: (uint8, uint8)) => t; f ((1, 2))",
    "val f = (t: (uint8, uint8)) => t; f({ (1, 2) })",
    "val f = (t: (uint8, uint8)) => t; f(if (true) then (1, 2) else { (3, 4) })",
    "val f = (x: int, t: (uint8, uint8)) => t; f(t: (1, 2), x: 3)",
    "val f = (): (uint8, uint8) => (1, 2); f()",
    "val f = (): (uint8, uint8) => { return (1, 2); }; f()",
    "val f = (flag: bool): (uint8, uint8) => { if (flag) { return (1, 2); }; (3, 4) }; f(true)",
    "val f = (flag: bool): (uint8, uint8) => if (flag) then (1, 2) else { (3, 4) }; f(false)",
    "val f = (): (uint8, uint8) => { val g = (): (uint8,) => (3,); val x = g(); (1, 2) }; f()",
    "val x = (255u8,); val t: (uint8, uint8) = (x[0], 2); t"
  })
  void given__tuple_context__when__fresh_pair_is_typed__then__expected_slots_are_used(String code) {
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    final var expected = new TyStruct(new TyField[]{
      new TyField(null, uint8),
      new TyField(null, uint8)
    });
    Assertions.assertTrue(TypeComparison.sameValueType(Inf.codeToThir(code).root().ty(), expected));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "uint8 | 0",
    "uint8 | 255",
    "uint8 | 0x99",
    "uint8 | 0b1111_1111",
    "uint8 | 0377",
    "uint8 | 25_5",
    "int8 | -128",
    "int8 | 127",
    "uint64 | 18446744073709551615",
    "int64 | -9223372036854775808",
    "int64 | 9223372036854775807",
    "int16 | -128i8",
    "int16 | 255u8",
    "uint16 | 255u8",
    "int64 | 1 + 2"
  })
  void given__fitting_literal_or_lossless_widening__when__typed__then__destination_layout(String destination, String source) {
    final var expected = Tys.fromString(destination, new MachineTarget(64));
    Assertions.assertEquals(
      new TyStruct(new TyField[]{new TyField(null, expected)}),
      Inf.codeToThir("val t: (%s,) = (%s,); t".formatted(destination, source)).root().ty()
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: ((uint8, uint8), int16) = ((1, 2), -128i8); t",
    "val t: ((uint8, uint8), int16) = ({ (1, 2) }, 255u8); t",
    "val f = (): ((uint8, uint8), int16) => { return ((1, 2), 255u8); }; f()",
    "val f = (t: ((uint8, uint8), int16)) => t; f(((1, 2), 255u8))"
  })
  void given__nested_construction__when__typed__then__context_reaches_each_fresh_tuple(String code) {
    final var target = new MachineTarget(64);
    final var uint8 = Tys.fromString("uint8", target);
    Assertions.assertEquals(new TyStruct(new TyField[]{
      new TyField(null, new TyStruct(new TyField[]{
        new TyField(null, uint8),
        new TyField(null, uint8)
      })),
      new TyField(null, Tys.fromString("int16", target))
    }), Inf.codeToThir(code).root().ty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (a: uint8, b: uint16) = (b = 256, a = 1i32)",
    "val t: (a: uint8, b: uint16) = (1u16, a = 256)",
    "val t: (a: uint8, bool) = (a = 1, 2)",
    "val t: (a: uint8, b: uint16) = (b = 1u8, a = 1L)",
    "val t: (uint8,) = (256,)",
    "val t: (uint8,) = (-1,)",
    "val t: (uint8,) = (0x100,)",
    "val t: (int8,) = (128,)",
    "val t: (int8,) = (-129,)",
    "val t: (uint64,) = (18446744073709551616,)",
    "val t: (int64,) = (9223372036854775808,)",
    "val t: (int64,) = (-9223372036854775809,)",
    "val t: (uint8,) = (1i32,)",
    "val t: (uint8,) = (1L,)",
    "val t: (uint8,) = (1 + 2,)",
    "val x = 1; val t: (uint8,) = (x,)",
    "val t: (uint16,) = (1i8,)",
    "val t: (int8,) = (1u8,)",
    "val t: (int16,) = (1u16,)",
    "val t: (uint8,) = (true,)",
    "val t: (float,) = (1,)",
    "val t: (double,) = (1.0f,)",
    "val t: (uint8, bool) = (1, 2)",
    "val t: (uint8, uint8) = (1,)",
    "val t: ((uint8,),) = ((1, 2),)",
    "val t: (uint8,) = (if (true) then 1 else 256,)",
    "val f = (): (uint8, bool) => ({ return (2, true); }, 3)",
    "val f = (t: (uint8,)) => 1; f({ (256,) })",
    "val f = (): (uint8,) => { return (256,); }"
  })
  void given__unsafe_or_incompatible_element_conversion__when__typed__then__explicit_error(String code) {
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
    Assertions.assertTrue(error.getMessage().contains("tuple"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = (): (uint8, bool) => (1, { return (2, true); }, ()); f()",
    "val f = (): (uint8, bool) => (1, { return (2, true); }, missing); f()"
  })
  void given__invalid_later_element_after_exit__when__typed__then__still_statically_checked(String code) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val existing = (1u8,); val t: (a: uint8,) = existing",
    "val existing = (b = true, a = 1); val t: (a: int, b: bool) = existing",
    "val existing = (1u8,); val t: (outer: (a: uint8,),) = (existing,)",
    "val f = (t: (a: int, b: bool)) => t; val existing = (1, true); f(existing)",
    "val f = (): (a: int, b: bool) => { val existing = (b = true, a = 1); return existing; }",
    "val existing = (1, true); val t: (a: int, b: bool) = { existing }",
    "val existing = (1, true); val t: (a: int, b: bool) = if (true) then (1, true) else existing",
    "val S = struct { val t: (a: int, b: bool); }; new heap S { t = (1, true); }",
    "val existing = (1u8,); val t: (uint16,) = existing",
    "val existing = (1,); val t: (uint8,) = existing",
    "val f = (t: (uint16,)) => t; val existing = (1u8,); f(existing)",
    "val f = (): (uint16,) => { val existing = (1u8,); return existing; }",
    "val existing = (1u8,); val f = (): (uint16,) => existing",
    "val existing = (1u8,); val t: ((uint16,),) = (existing,)",
    "val existing = (1u8,); val t: (uint16,) = { existing }",
    "val existing = (1u8,); val t: (uint16,) = if (true) then (1,) else existing",
    "val f = () => (1u8,); val t: (uint16,) = f()",
    "val values = [(1u8,)]; val t: (uint16,) = values[0]",
    "val S = struct { val t: (uint8,); }; new heap S { t = (1,); }",
    "val S = struct { val t: (uint8,); }; val s = new heap S { t = (1u8,); }; s.t = (1,)",
    "val values = [(1u8,)]; values[0] = (1,)",
    "val values = [(1u8,), (1,)]"
  })
  void given__existing_tuple_or_aggregate_storage_context__when__conversion_is_needed__then__rejected(String code) {
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (uint8,) = ({ val x = 999; 1 },); t",
    "val f = (): (uint8,) => { val g = () => 999; val x = g(); (1,) }; f()",
    "val f = (t: (uint8,), x: int) => t; f((1,), 999)",
    "val t: (uint8,) = (if (999 > 0) then 1 else 2,); t"
  })
  void given__tuple_context__when__unrelated_literals_are_visited__then__their_types_do_not_change(String code) {
    final var literals = new ArrayList<Hir.Literal>();
    Inf.codeToThir(code).root().visit(new HirVisitor() {
      @Override
      public void visitLiteral(Hir.Literal literal) {
        if (literal.content().equals("999")) {
          literals.add(literal);
        }
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertFalse(literals.isEmpty()),
      () -> Assertions.assertTrue(literals.stream().allMatch(literal ->
        literal.ty().equals(Ty.INTEGER)))
    );
  }
}
