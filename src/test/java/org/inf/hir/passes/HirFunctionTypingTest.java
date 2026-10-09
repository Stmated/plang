package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirFunctionTypingTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "uint8",
    "bool"
  })
  void given__inferred_parameter__when__contextual_typing_is_repeated__then__annotation_binding_and_signature_are_stable(
    final String type
  ) {
    final var signature = signature("val Fn = (value: %s): %s; val f: Fn = (p) => p; f".formatted(type, type));
    final var parameter = signature.parameters()[0];
    final var annotation = parameter.typeAnnotation();
    final var destination = Tys.fromString(type, new MachineTarget(64));
    final var expected = new TyFn(new TyParam[]{new TyParam("value", destination)}, false, destination);

    HirFunctionTyping.contextualize(signature, expected);
    HirTyCommonVisitorPass.pass(signature);
    final var resolved = signature.ty();
    HirFunctionTyping.contextualize(signature, expected);
    Assertions.assertSame(resolved, signature.ty());
    HirTyCommonVisitorPass.resolveAvailableTypes(signature);
    HirTyCommonVisitorPass.pass(signature);

    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, parameter.typeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertSame(destination, parameter.resolvedTy()),
      () -> Assertions.assertSame(destination, signature.ty().parameters()[0].ty()),
      () -> Assertions.assertEquals("p", signature.ty().parameters()[0].name())
    );
  }

  @Test
  void given__previous_contextual_binding__when__the_expected_type_changes__then__binding_and_signature_can_be_refreshed() {
    final var signature = signature("val Fn = (value: bool): bool; val f: Fn = (p) => p; f");
    final var parameter = signature.parameters()[0];
    final var annotation = parameter.typeAnnotation();
    HirFunctionTyping.contextualize(signature, new TyFn(new TyParam[]{new TyParam("value", Ty.BOOLEAN)}, false, Ty.BOOLEAN));
    HirTyCommonVisitorPass.pass(signature);
    Assertions.assertEquals(Ty.BOOLEAN, signature.ty().parameters()[0].ty());
    final var returnType = signature.ty().returnTy();

    HirFunctionTyping.contextualize(signature, new TyFn(new TyParam[]{new TyParam("value", Ty.LONG)}, false, Ty.LONG));
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, parameter.typeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertEquals(Ty.LONG, parameter.resolvedTy()),
      () -> Assertions.assertEquals(Ty.LONG, signature.ty().parameters()[0].ty()),
      () -> Assertions.assertSame(returnType, signature.ty().returnTy())
    );
    HirTyCommonVisitorPass.pass(signature);
    Assertions.assertEquals(Ty.LONG, signature.ty().parameters()[0].ty());
  }

  @Test
  void given__resolved_inferred_return__when__parameter_context_changes__then__return_is_retained_until_body_is_retyped() {
    final var signature = signature("val Fn = (p: bool, q: int): bool; val f: Fn = (p: bool, q) => p; f");
    final var annotation = signature.returnTypeAnnotation();
    final var resolvedReturn = signature.ty().returnTy();
    final var expected = new TyFn(
      new TyParam[]{new TyParam("p", Ty.BOOLEAN), new TyParam("q", Ty.LONG)}, false, Ty.LONG
    );

    HirFunctionTyping.contextualize(signature, expected);
    HirTyCommonVisitorPass.pass(signature);

    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, signature.returnTypeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertSame(resolvedReturn, signature.ty().returnTy()),
      () -> Assertions.assertSame(Ty.LONG, signature.ty().parameters()[1].ty())
    );
  }

  @Test
  void given__explicit_parameter_constraint__when__contextualized__then__annotation_binding_and_signature_are_not_rewritten() {
    final var signature = signature("val Fn = (value: bool): bool; val f = (p: Fn) => p(true); f");
    final var parameter = signature.parameters()[0];
    final var annotation = parameter.typeAnnotation();
    final var resolved = parameter.resolvedTy();
    final var function = signature.ty();

    HirFunctionTyping.contextualize(signature, new TyFn(new TyParam[]{new TyParam("value", Ty.LONG)}, false, Ty.LONG));

    Assertions.assertAll(
      () -> Assertions.assertInstanceOf(Hir.Identifier.class, annotation.expression()),
      () -> Assertions.assertSame(annotation.expression(), parameter.typeAnnotation().expression()),
      () -> Assertions.assertSame(resolved, parameter.resolvedTy()),
      () -> Assertions.assertSame(function, signature.ty())
    );
  }

  private static Hir.FunctionSignature signature(final String code) {
    final var functions = new ArrayList<Hir.Function>();
    Inf.codeToThir(code).root().visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        functions.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    Assertions.assertEquals(1, functions.size());
    return functions.getFirst().signature();
  }
}
