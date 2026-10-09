package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HirFnTyVisitorPassTest {

  @Test
  void given__inferred_return_and_changed_parameters__when__signature_is_rebuilt__then__resolved_return_is_retained() {
    final var annotation = new Hir.DynamicTy(Ty.INFER);
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), new Hir.DynamicTy(Ty.INFER), false, Ty.LONG);
    final var signature = new Hir.FunctionSignature(
      new Hir.Parameter[]{parameter}, true, annotation,
      new TyFn(new TyParam[]{new TyParam("p", Ty.INTEGER)}, true, Ty.BOOLEAN)
    );
    final var type = HirFnTyVisitorPass.fnToTyFn(signature);
    Assertions.assertAll(
      () -> Assertions.assertSame(Ty.LONG, type.parameters()[0].ty()),
      () -> Assertions.assertSame(Ty.BOOLEAN, type.returnTy()),
      () -> Assertions.assertTrue(type.vararg()),
      () -> Assertions.assertSame(annotation, signature.returnTypeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty())
    );
  }

  @Test
  void given__resolved_parameter__when__signature_is_constructed__then__binding_is_used_without_annotation_resolution() {
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), new Hir.DynamicTy(Ty.LONG), false, Ty.INTEGER);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{parameter}, false, new Hir.DynamicTy(Ty.BOOLEAN));
    final var type = HirFnTyVisitorPass.fnToTyFn(signature);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertSame(Ty.INTEGER, parameter.resolvedTy()),
      () -> Assertions.assertSame(parameter.resolvedTy(), type.parameters()[0].ty()),
      () -> Assertions.assertEquals("p", type.parameters()[0].name()),
      () -> Assertions.assertEquals(Ty.BOOLEAN, type.returnTy()),
      () -> Assertions.assertEquals(Ty.LONG, parameter.typeAnnotation().ty())
    );
  }
}
