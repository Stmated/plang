package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirIntegerLiteralTypingVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val n: uint8 = 17; n",
    "var n: uint8 = 17; n = 18; n",
    "var n = 17u8; n = 18; n",
    "var n: uint8 = 17; n += 18; n",
    "val n: uint8 = { val unrelated = 999; 17 }; n",
    "val n: uint8 = if (999 > 0) then 17 else 18; n",
    "val read = (n: uint8) => n; read(17)",
    "val read = (n: uint8) => n; read(n = 17)",
    "val read = (a: uint8, b: uint8) => a; read(...(b = 18, a = 17))",
    "val read = (a: uint8, b: uint8) => a; read(...(17,), b = 18)",
    "val read = (n: uint8) => n; read(...(ignored = 999, n = 17))",
    "val read = (n: uint8) => n; val service = (call = read,); service.call(17)",
    "val make = (): uint8 => 17; make()",
    "val make = (): uint8 => { return 17; }; make()",
    "val make = (): uint8 => { val nested = () => 999; if (true) then { return 17; }; 18 }; make()",
    "val Fn = (): uint8; val make: Fn = () => 17; make()",
    "val Fn = (): uint8; val Factory = (): Fn; val make: Factory = () => (() => 17); make()()",
    "val read = (make: (): uint8) => make(); read(() => 17)",
    "val values = [17, 18; uint8;2]; values",
    "val values = [{ val unrelated = 999; 17 }, if (true) then 17 else 18; uint8;2]; values",
    "val values: [;uint8;1] = [17]; values",
    "val values = [17u8]; values[0] = 18; values",
    "val read = (values: [;uint8;1]) => values; read([17])",
    "val t: ([;uint8;1],) = ([17],); t",
    "val S = struct { val n: uint8; }; val s = new heap S { n = 17; }; s.n = 18; s",
    "val S = struct { val t: (uint8,); }; new heap S { t = (17,); }",
    "val S = struct { val t: (uint8,); }; val s = new heap S { t = (17u8,); }; s.t = (18,); s",
    "val values = [(17u8,)]; values[0] = (18,); values",
    "val values = [(17,); (uint8,);1]; values"
  })
  void given__resolved_integer_context__when__literals_are_typed__then__the_same_rule_applies_at_each_use_site(final String code) {
    final var root = Inf.codeToThir(code).root();
    final var literals = literals(root);
    final var results = literals.stream().filter(literal -> literal.content().matches("17|18")).toList();
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    Assertions.assertAll(
      () -> Assertions.assertFalse(results.isEmpty()),
      () -> Assertions.assertTrue(results.stream().allMatch(literal -> TypeComparison.sameValueType(literal.ty(), uint8))),
      () -> Assertions.assertTrue(literals.stream().filter(literal -> literal.content().equals("999"))
        .allMatch(literal -> literal.ty().equals(Ty.INTEGER))),
      () -> Assertions.assertDoesNotThrow(() -> Inf.codeToMir(code))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val n: uint8 = 256; n",
    "val n: uint8 = -1; n",
    "val n: int8 = 128; n",
    "var n = 1u8; n = 256; n",
    "var n = 1u8; n += 256; n",
    "val read = (n: uint8) => n; read(256)",
    "val read = (n: uint8) => n; read(n = -1)",
    "val read = (n: uint8) => n; read(...(256,))",
    "val make = (): uint8 => 256; make()",
    "val make = (): uint8 => { return -1; }; make()",
    "val Fn = (): uint8; val make: Fn = () => 256; make()",
    "val read = (make: (): uint8) => make(); read(() => 256)",
    "val values = [256; uint8;1]; values",
    "val values = [-1; uint8;1]; values",
    "val values = [128; int8;1]; values",
    "val values: [;uint8;1] = [256]; values",
    "val S = struct { val n: uint8; }; new heap S { n = 256; }"
  })
  void given__out_of_range_integer_context__when__typed__then__the_literal_is_rejected(final String code) {
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
    Assertions.assertTrue(error.getMessage().contains("Integer literal does not fit expected type"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "uint8 | 0 | 0",
    "uint8 | 255 | 255",
    "uint8 | 0x99 | 153",
    "uint8 | 0b1111_1111 | 255",
    "uint8 | 25_5 | 255",
    "int8 | -128 | -128",
    "int8 | 127 | 127",
    "int8 | -0x80 | -128",
    "uint64 | 18446744073709551615 | 18446744073709551615",
    "int64 | -9223372036854775808 | -9223372036854775808"
  })
  void given__fitting_literal__when__contextualized__then__range_boundaries_and_numeric_value_are_preserved(
    final String destination, final String literal, final String content
  ) {
    final var code = "val n: %s = %s; n".formatted(destination, literal);
    final var values = literals(Inf.codeToThir(code).root());
    Assertions.assertEquals(1, values.size());
    Assertions.assertAll(
      () -> Assertions.assertEquals(Tys.fromString(destination, new MachineTarget(64)), values.getFirst().ty()),
      () -> Assertions.assertEquals(content, values.getFirst().content()),
      () -> Assertions.assertDoesNotThrow(() -> Inf.codeToMir(code))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val n = 1; n",
    "val values = [1]; values",
    "val make = () => 1; make()",
    "val n: double = 1; n",
    "val n: uint8 = 1 + 2; n",
    "val n = 1; val t: uint8 = n; t"
  })
  void given__no_literal_context__when__the_pass_runs__then__default_integer_types_are_preserved(final String code) {
    final var root = prepare(code);
    final var before = literals(root).stream().map(Hir.Literal::ty).toList();
    HirIntegerLiteralTypingVisitorPass.pass(root);
    Assertions.assertEquals(before, literals(root).stream().map(Hir.Literal::ty).toList());
  }

  @Test
  void given__different_named_parameter_types__when__arguments_are_bound__then__each_literal_uses_its_own_parameter_type() {
    final var root = Inf.codeToThir("""
      val read = (small: uint8, large: uint16) => small;
      read(large = 1000, small = 255)
      """).root();
    final var values = literals(root);
    Assertions.assertEquals(2, values.size());
    Assertions.assertAll(
      () -> Assertions.assertEquals(Tys.fromString("uint16", new MachineTarget(64)), values.getFirst().ty()),
      () -> Assertions.assertEquals(Tys.fromString("uint8", new MachineTarget(64)), values.getLast().ty())
    );
  }

  @Test
  void given__nondecimal_destination__when__a_literal_is_assigned__then__its_digits_use_the_destination_radix() {
    final var root = Inf.codeToThir("var n = 0x1; n = 153; n").root();
    final var values = literals(root);
    Assertions.assertEquals(2, values.size());
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INTEGER_HEX, values.getLast().ty()),
      () -> Assertions.assertEquals("99", values.getLast().content())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (uint8,) = (1,); t",
    "val t: ((uint8,),) = ((1,),); t",
    "val t: (uint8,) = ({ val unrelated = 999; 1 },); t",
    "val t: (uint8,) = (if (true) then 1 else 2,); t"
  })
  void given__linked_integer_slots__when__literals_are_adapted__then__only_result_literals_change(final String code) {
    final var root = prepare(code);
    final var beforeRoot = root.ty();
    final var tuples = new ArrayList<Hir.Tuple>();
    root.visit(new HirVisitor() {
      @Override
      public void visitTuple(final Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }
    });
    final var beforeTypes = tuples.stream().map(Hir.Tuple::ty).toList();
    HirIntegerLiteralTypingVisitorPass.pass(root);
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal expression) {
        literals.add(expression);
      }

      @Override
      public void visitConvert(final Hir.Convert expression) {
        Assertions.fail("Literal adaptation must not insert conversions");
      }
    });
    final var uint8 = Tys.fromString("uint8", new MachineTarget(64));
    Assertions.assertAll(
      () -> Assertions.assertEquals(beforeRoot, root.ty()),
      () -> Assertions.assertEquals(beforeTypes, tuples.stream().map(Hir.Tuple::ty).toList()),
      () -> Assertions.assertTrue(literals.stream().filter(literal -> literal.content().matches("[12]"))
        .allMatch(literal -> literal.ty().equals(uint8))),
      () -> Assertions.assertTrue(literals.stream().filter(literal -> literal.content().equals("999"))
        .allMatch(literal -> literal.ty().equals(Ty.INTEGER)))
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "uint8 | 256",
    "uint8 | -1",
    "int8 | 128",
    "int8 | -129",
    "uint64 | 18446744073709551616"
  })
  void given__out_of_range_literal__when__literals_are_adapted__then__the_slot_error_is_reported(
    final String destination, final String literal
  ) {
    final var root = prepare("val t: (%s,) = (%s,); t".formatted(destination, literal));
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirIntegerLiteralTypingVisitorPass.pass(root));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "255u8 | uint8",
    "1i32 | int32",
    "1L | int64"
  })
  void given__explicit_integer_type__when__literals_are_adapted__then__the_source_type_is_preserved(
    final String literal, final String source
  ) {
    for (final var template : new String[]{
      "val t: (uint16,) = (%s,); t",
      "val n: uint16 = %s; n",
      "val values = [%s; uint16;]; values",
      "val read = (n: uint16) => n; read(%s)",
      "val make = (): uint16 => %s; make()"
    }) {
      final var root = prepare(template.formatted(literal));
      final var values = literals(root);
      Assertions.assertEquals(1, values.size());
      final var value = values.getFirst();
      final var beforeType = value.ty();
      final var beforeContent = value.content();
      HirIntegerLiteralTypingVisitorPass.pass(root);
      Assertions.assertAll(
        () -> Assertions.assertSame(beforeType, value.ty()),
        () -> Assertions.assertEquals(beforeContent, value.content()),
        () -> Assertions.assertTrue(TypeComparison.sameValueType(Tys.fromString(source, new MachineTarget(64)), value.ty()))
      );
    }
  }

  @Test
  void given__repeated_literal_typing__when__the_pass_runs_again__then__literal_types_and_content_are_stable() {
    final var root = prepare("val read = (n: uint8) => n; read(0x99)");
    final var beforeRoot = root.ty();
    HirIntegerLiteralTypingVisitorPass.pass(root);
    final var beforeTypes = literals(root).stream().map(Hir.Literal::ty).toList();
    final var beforeContents = literals(root).stream().map(Hir.Literal::content).toList();
    HirIntegerLiteralTypingVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(beforeRoot, root.ty()),
      () -> Assertions.assertEquals(beforeTypes, literals(root).stream().map(Hir.Literal::ty).toList()),
      () -> Assertions.assertEquals(beforeContents, literals(root).stream().map(Hir.Literal::content).toList())
    );
  }

  private static ArrayList<Hir.Literal> literals(final Hir.Expression root) {
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal expression) {
        literals.add(expression);
      }
    });
    return literals;
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTupleContextVisitorPass.pass(root);
    return root;
  }
}
