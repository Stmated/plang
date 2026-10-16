package org.inf.hir.typing;

import org.inf.hir.DynamicTyDefault;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Objects;
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
    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), null, false, null);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{parameter}, false, new DynamicTyDefault());
    final var expected = new TyFn(new TyParam[]{new TyParam("value", destination)}, false, destination);

    HirFunctionTyping.adhereToConstraint(signature, expected);
    final var resolved = signature.ty();
    HirFunctionTyping.adhereToConstraint(signature, expected);

    Assertions.assertAll(
      () -> Assertions.assertSame(resolved, signature.ty()),
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty().completionTy()),
      () -> Assertions.assertSame(destination, parameter.ty().structuralTy()),
      () -> Assertions.assertTrue(Tys.isInferred(parameter.ty().declaredConstraintTy())),
      () -> Assertions.assertSame(destination, signature.ty().parameters()[0].ty()),
      () -> Assertions.assertEquals("p", signature.ty().parameters()[0].name()),
      () -> Assertions.assertEquals(Ty.INFER, signature.ty().returnTy())
    );
  }

  @Test
  void given__previous_contextual_binding__when__the_expected_type_changes__then__binding_and_signature_can_be_refreshed() {

    final var parameter = new Hir.Parameter(new Hir.Lexeme("p"), null, false, Ty.BOOLEAN);
    final var resolved = new TyFn(new TyParam[]{new TyParam("p", Ty.BOOLEAN)}, false, Ty.BOOLEAN);
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{parameter}, false, new DynamicTyDefault(), resolved);

    HirFunctionTyping.adhereToConstraint(signature, new TyFn(new TyParam[]{new TyParam("value", Ty.LONG)}, false, Ty.LONG));

    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.LONG, parameter.ty().structuralTy(), "p"),
      () -> Assertions.assertTrue(Tys.isInferred(parameter.ty().declaredConstraintTy()), "c inferred"),
      () -> Assertions.assertEquals(Ty.LONG, signature.ty().parameters()[0].ty(), "p ty"),
      () -> Assertions.assertNotSame(resolved, signature.ty(), "fn"),
      () -> Assertions.assertSame(resolved.returnTy(), signature.ty().returnTy(), "ret")
    );
  }

  @Test
  void given__resolved_inferred_return__when__parameter_context_changes__then__return_is_retained() {

    final var explicit = new Hir.Parameter(new Hir.Lexeme("p"), Hir.BuiltInTy.fromString("bool", MachineTarget.T64), false, Ty.BOOLEAN);
    final var inferred = new Hir.Parameter(new Hir.Lexeme("q"), null, false, Ty.INTEGER);
    final var resolved = new TyFn(
      new TyParam[]{new TyParam("p", Ty.BOOLEAN), new TyParam("q", Ty.INTEGER)}, false, Ty.BOOLEAN
    );
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[]{explicit, inferred}, false, new DynamicTyDefault(), resolved);
    final var expected = new TyFn(
      new TyParam[]{new TyParam("p", Ty.BOOLEAN), new TyParam("q", Ty.LONG)}, false, Ty.LONG
    );

    HirFunctionTyping.adhereToConstraint(signature, expected);

    Assertions.assertAll(
      () -> Assertions.assertSame(resolved.returnTy(), signature.ty().returnTy()),
      () -> Assertions.assertSame(Ty.BOOLEAN, explicit.ty().structuralTy()),
      () -> Assertions.assertSame(Ty.LONG, inferred.ty().structuralTy()),
      () -> Assertions.assertSame(Ty.LONG, signature.ty().parameters()[1].ty())
    );
  }

  @Test
  void given__explicit_parameter_constraint__when__contextualized__then__annotation_binding_and_signature_are_not_rewritten() {

    final var intExpr = Objects.requireNonNull(Hir.BuiltInTy.fromString("int", MachineTarget.T64));
    final var signature = new Hir.FunctionSignature(
      new DynamicTyDefault(),
      new Hir.Parameter(new Hir.Lexeme("p"), intExpr, false, intExpr.ty())
    );

    final var before = signature.ty();
    HirFunctionTyping.adhereToConstraint(signature, new TyFn(Ty.LONG, new TyParam("value", Ty.LONG)));
    final var after = signature.ty();

    // Function should not have been updated by constraint, since it already has explicit ones on its own.
    Assertions.assertSame(before, after);
  }
}
