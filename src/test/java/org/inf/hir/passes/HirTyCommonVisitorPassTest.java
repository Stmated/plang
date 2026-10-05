package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class HirTyCommonVisitorPassTest {

  @Test
  void given__expected_tuple_type__when__only_common_inference_runs__then__no_contextual_rewriting() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val t: (uint8,) = (1,); t"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.pass(root);
    final var literals = new ArrayList<Hir.Literal>();
    root.visit(new HirVisitor() {
      @Override
      public void visitLiteral(Hir.Literal literal) {
        literals.add(literal);
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, literals.size()),
      () -> Assertions.assertEquals(Ty.INTEGER, literals.getFirst().ty()),
      () -> Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirTupleValidationVisitorPass.pass(root))
    );
  }

  @Test
  void given__typed_binary_children__when__single_node_is_resolved__then__children_are_not_retyped() {
    final var root = Inf.codeToThir("1 + 2 * 3").root();
    final var binaries = new ArrayList<Hir.BinaryOperation>();
    root.visit(new HirVisitor() {
      @Override
      public void visitBinaryOperation(Hir.BinaryOperation binary) {
        binaries.add(binary);
        HirVisitor.super.visitBinaryOperation(binary);
      }
    });
    final var outer = binaries.getFirst();
    final var inner = binaries.getLast();
    inner.ty(Ty.LONG);
    inner.valueTy(Ty.LONG);
    outer.ty(null);
    outer.valueTy(null);

    HirTyCommonVisitorPass.resolveNode(outer);

    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.LONG, inner.ty()),
      () -> Assertions.assertEquals(Ty.LONG, inner.valueTy()),
      () -> Assertions.assertEquals(Ty.LONG, outer.ty()),
      () -> Assertions.assertEquals(Tys.union(Ty.INTEGER, Ty.LONG), outer.valueTy())
    );
  }
}
