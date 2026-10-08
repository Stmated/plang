package org.inf.hir;

import org.inf.Inf;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class HirConvertTest {

  private static Hir.Convert conversion() {
    final var conversions = new ArrayList<Hir.Convert>();
    Inf.codeToThir("val t: (int16,) = (255u8,); t").root().visit(new HirVisitor() {
      @Override
      public void visitConvert(Hir.Convert conversion) {
        conversions.add(conversion);
      }
    });
    return conversions.getFirst();
  }

  @Test
  void given__conversion__when__visited__then__operand_is_visited() {
    final var conversion = conversion();
    final var operands = new ArrayList<Hir.Literal>();
    conversion.visit(new HirVisitor() {
      @Override
      public void visitLiteral(Hir.Literal literal) {
        operands.add(literal);
      }
    });
    Assertions.assertEquals(1, operands.size());
    Assertions.assertSame(conversion.expression(), operands.getFirst());
  }

  @Test
  void given__conversion__when__transformed__then__operand_changes_without_losing_destination_type() {
    final var conversion = conversion();
    final var targetType = conversion.targetTy();
    final var transformed = Assertions.assertInstanceOf(Hir.Convert.class, conversion.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformLiteral(Hir.Literal literal) {
        return new Hir.Literal("254u8", literal.ty());
      }
    }));
    final var operand = Assertions.assertInstanceOf(Hir.Literal.class, transformed.expression());
    Assertions.assertAll(
      () -> Assertions.assertEquals("254u8", operand.content()),
      () -> Assertions.assertSame(targetType, transformed.targetTy()),
      () -> Assertions.assertSame(targetType, transformed.ty())
    );
  }

  @Test
  void given__conversion_of_nonreturning_operand__when__typed__then__flow_and_executed_return_type_are_preserved() {
    final var conversion = new Hir.Convert(new Hir.Return(new Hir.Literal("7", Ty.INTEGER)), Ty.LONG);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(conversion));
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.DEADEND, conversion.ty()),
      () -> Assertions.assertEquals(Ty.LONG, conversion.targetTy()),
      () -> Assertions.assertEquals(Ty.INTEGER, program.ty())
    );
  }
}
