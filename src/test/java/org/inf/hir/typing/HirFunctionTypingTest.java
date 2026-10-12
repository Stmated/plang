package org.inf.hir.typing;

import org.inf.hir.DynamicTy;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class HirFunctionTypingTest {

  private static Stream<Ty> parameterTypes() {
    return Stream.of(
      Ty.CHAR,
      Ty.BOOLEAN
    );
  }

  @ParameterizedTest
  @MethodSource("parameterTypes")
  void given__inferred_parameter__when__contextual_typing_is_repeated__then__annotation_binding_and_signature_are_stable(
    final Ty destination
  ) {
    final var annotation = new DynamicTy();
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), annotation, false, null);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{parameter}, false, new DynamicTy());
    final var expected = new TyFn(new TyParam[]{new TyParam("value", destination)}, false, destination);

    HirFunctionTyping.adhereToConstraint(signature, expected);
    final var resolved = signature.ty();
    HirFunctionTyping.adhereToConstraint(signature, expected);

    Assertions.assertAll(
      () -> Assertions.assertSame(resolved, signature.ty()),
      () -> Assertions.assertSame(annotation, parameter.typeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertSame(destination, parameter.resolvedTy()),
      () -> Assertions.assertSame(destination, signature.ty().parameters()[0].ty()),
      () -> Assertions.assertEquals("p", signature.ty().parameters()[0].name()),
      () -> Assertions.assertEquals(Ty.INFER, signature.ty().returnTy())
    );
  }

  @Test
  void given__previous_contextual_binding__when__the_expected_type_changes__then__binding_and_signature_can_be_refreshed() {
    final var annotation = new DynamicTy();
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), annotation, false, Ty.BOOLEAN);
    final var resolved = new TyFn(new TyParam[]{new TyParam("p", Ty.BOOLEAN)}, false, Ty.BOOLEAN);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{parameter}, false, new DynamicTy(), resolved);

    HirFunctionTyping.adhereToConstraint(signature, new TyFn(new TyParam[]{new TyParam("value", Ty.LONG)}, false, Ty.LONG));

    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, parameter.typeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertEquals(Ty.LONG, parameter.resolvedTy()),
      () -> Assertions.assertEquals(Ty.LONG, signature.ty().parameters()[0].ty()),
      () -> Assertions.assertNotSame(resolved, signature.ty()),
      () -> Assertions.assertSame(resolved.returnTy(), signature.ty().returnTy())
    );
  }

  @Test
  void given__resolved_inferred_return__when__parameter_context_changes__then__return_is_retained() {
    final var annotation = new DynamicTy();
    final var explicit = new Hir.Parameter(new Hir.Lexeme("p"), new DynamicTy(Ty.BOOLEAN), false, Ty.BOOLEAN);
    final var inferred = new Hir.Parameter(new Hir.Lexeme("q"), new DynamicTy(), false, Ty.INTEGER);
    final var resolved = new TyFn(
      new TyParam[]{new TyParam("p", Ty.BOOLEAN), new TyParam("q", Ty.INTEGER)}, false, Ty.BOOLEAN
    );
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{explicit, inferred}, false, annotation, resolved);
    final var expected = new TyFn(
      new TyParam[]{new TyParam("p", Ty.BOOLEAN), new TyParam("q", Ty.LONG)}, false, Ty.LONG
    );

    HirFunctionTyping.adhereToConstraint(signature, expected);

    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, signature.returnTypeAnnotation()),
      () -> Assertions.assertEquals(Ty.INFER, annotation.ty()),
      () -> Assertions.assertSame(resolved.returnTy(), signature.ty().returnTy()),
      () -> Assertions.assertSame(Ty.BOOLEAN, explicit.resolvedTy()),
      () -> Assertions.assertSame(Ty.LONG, inferred.resolvedTy()),
      () -> Assertions.assertSame(Ty.LONG, signature.ty().parameters()[1].ty())
    );
  }

  @Test
  void given__explicit_parameter_constraint__when__contextualized__then__annotation_binding_and_signature_are_not_rewritten() {
    final var resolved = new TyFn(new TyParam[]{new TyParam("value", Ty.BOOLEAN)}, false, Ty.BOOLEAN);
    final var annotation = new DynamicTy(resolved, new Hir.Identifier("Fn", null));
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), annotation, false, resolved);
    final var function = new TyFn(new TyParam[]{new TyParam("p", resolved)}, false, Ty.BOOLEAN);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{parameter}, false, new DynamicTy(), function);

    HirFunctionTyping.adhereToConstraint(signature, new TyFn(new TyParam[]{new TyParam("value", Ty.LONG)}, false, Ty.LONG));

    Assertions.assertAll(
      () -> Assertions.assertSame(annotation, parameter.typeAnnotation()),
      () -> Assertions.assertSame(annotation.expression(), parameter.typeAnnotation().expression()),
      () -> Assertions.assertSame(resolved, parameter.resolvedTy()),
      () -> Assertions.assertSame(function, signature.ty())
    );
  }
}
