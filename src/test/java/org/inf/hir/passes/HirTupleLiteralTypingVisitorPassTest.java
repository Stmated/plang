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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirTupleLiteralTypingVisitorPassTest {

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
    HirTupleLiteralTypingVisitorPass.pass(root);
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
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirTupleLiteralTypingVisitorPass.pass(root));
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
    final var root = prepare("val t: (uint16,) = (%s,); t".formatted(literal));
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(final Hir.Literal expression) {
        literals.add(expression);
      }
    });
    Assertions.assertEquals(1, literals.size());
    final var value = literals.getFirst();
    final var beforeType = value.ty();
    final var beforeContent = value.content();
    HirTupleLiteralTypingVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertSame(beforeType, value.ty()),
      () -> Assertions.assertEquals(beforeContent, value.content()),
      () -> Assertions.assertTrue(TypeComparison.sameValueType(Tys.fromString(source, new MachineTarget(64)), value.ty()))
    );
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirTupleContextVisitorPass.pass(root);
    return root;
  }
}
