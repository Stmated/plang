package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirFunctionParameterValidationVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = () => 1; f",
    "val f: Fn = (a, b) => 1; f",
    "val f: Fn = (v, ...) => v; f",
    "val f: Fn = (a, b) => a[0]; f",
    "val f: Fn = (a, b) => (1, 2)[5]; f",
    "val S = struct { val fn: Fn; }; new heap S { fn = (a, b) => a[0]; }",
    "val apply = (fn: Fn) => fn(5); apply((a, b) => a[0])",
    "val f: Fn = { (a, b) => a[0]; }; f",
    "val f: Fn = if (true) then ((v) => v) else ((a, b) => a[0]); f",
    "val make = (): Fn => { return ((a, b) => a[0]); }; make",
    "val Factory = (): Fn; val factory: Factory = () => ((a, b) => a[0]); factory",
    "val Factory = (): Fn; val factory: Factory = () => { return ((a, b) => a[0]); }; factory",
    "val apply = (x: int, fn: Fn) => fn(x); apply(fn = (a, b) => a[0], x = 5)",
    "val apply = (x: int, fn: Fn) => fn(x); apply(...(5,), (a, b) => a[0])",
    "val apply = (x: int, fn: Fn) => fn(x); apply(...(x = 5,), fn = (a, b) => a[0])"
  })
  void given__incompatible_lambda_shape__when__parameters_are_validated__then__rejected_before_dependent_body_typing(
    final String expression
  ) {
    final var root = prepare("val Fn = (v: int): int; %s".formatted(expression));
    final var error = Assertions.assertThrows(IllegalArgumentException.class,
      () -> HirFunctionParameterValidationVisitorPass.pass(root));
    Assertions.assertEquals("Lambda parameter count or variadic shape does not match expected function type", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = (v: bool) => 1; f",
    "val f: Fn = (v: bool) => v.missing; f",
    "val S = struct { val fn: Fn; }; new heap S { fn = (v: bool) => 1; }",
    "val apply = (fn: Fn) => fn(5); apply((v: bool) => 1)",
    "val make = (): Fn => { return ((v: bool) => v.missing); }; make",
    "val Factory = (): Fn; val factory: Factory = () => ((v: bool) => v.missing); factory",
    "val apply = (x: int, fn: Fn) => fn(x); apply(fn = (v: bool) => v.missing, x = 5)",
    "val apply = (x: int, fn: Fn) => fn(x); apply(...(5,), (v: bool) => v.missing)"
  })
  void given__incompatible_parameter_annotation__when__validated__then__annotation_is_rejected_without_rewriting(
    final String expression
  ) {
    final var root = prepare("val Fn = (v: int): int; %s".formatted(expression));
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertAll(
      () -> Assertions.assertThrows(InvalidTypeConversionException.class,
        () -> HirFunctionParameterValidationVisitorPass.pass(root)),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = (v) => true; f",
    "val S = struct { val fn: Fn; }; new heap S { fn = (v) => true; }",
    "val apply = (fn: Fn) => fn(5); apply((v) => true)"
  })
  void given__incompatible_return__when__only_parameters_are_validated__then__return_validation_is_deferred(
    final String expression
  ) {
    final var root = prepare("val Fn = (v: int): int; %s".formatted(expression));
    Assertions.assertDoesNotThrow(() -> HirFunctionParameterValidationVisitorPass.pass(root));
  }

  @Test
  void given__contextually_resolved_parameters__when__validated__then__no_inference_or_mutation_occurs() {
    final var root = prepare("val Fn = (v: int): int; val f: Fn = (v) => v; f");
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    HirFunctionParameterValidationVisitorPass.pass(root);
    Assertions.assertEquals(before, printer.render(root));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = { (a, b) => a[0]; (v) => v; }; f",
    "val f: Fn = { val unrelated = (a, b) => a[0]; (v) => v; }; f",
    "val make = (): Fn => { val unrelated = () => { return ((a, b) => a[0]); }; return ((v) => v); }; make",
    "val Factory = (): Fn; val factory: Factory = () => { val unrelated = () => { return ((a, b) => a[0]); }; (v) => v; }; factory",
    "val f: Fn = if (((a, b) => a[0]) == ((a, b) => a[0])) then ((v) => v) else ((v) => v); f"
  })
  void given__unrelated_lambdas__when__result_parameters_are_validated__then__no_context_leaks_into_them(final String expression) {
    final var root = prepare("val Fn = (v: int): int; %s".formatted(expression));
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertAll(
      () -> Assertions.assertDoesNotThrow(() -> HirFunctionParameterValidationVisitorPass.pass(root)),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @Test
  void given__unresolved_parameters__when__validation_runs_without_inference__then__it_rejects_without_resolving_them() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val Fn = (v: int): int; val f: Fn = (v) => v; f"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function expression) {
        functions.add(expression);
        HirVisitor.super.visitFunction(expression);
      }
    });
    Assertions.assertEquals(1, functions.size());
    Assertions.assertAll(
      () -> Assertions.assertThrows(InvalidTypeConversionException.class,
        () -> HirFunctionParameterValidationVisitorPass.pass(root)),
      () -> Assertions.assertTrue(Tys.isInferred(functions.getFirst().signature().parameters()[0].valueType().ty())),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = (a, b) => a[0]; f",
    "val f: Fn = (a, b) => (1, 2)[5]; f",
    "val S = struct { val fn: Fn; }; new heap S { fn = (a, b) => a[0]; }",
    "val apply = (fn: Fn) => fn(5); apply((a, b) => a[0])",
    "val Factory = (): Fn; val factory: Factory = () => ((a, b) => a[0]); factory"
  })
  void given__invalid_shape_and_dependent_body__when__the_pipeline_runs__then__parameter_diagnostic_is_preserved(
    final String expression
  ) {
    final var error = Assertions.assertThrows(IllegalArgumentException.class,
      () -> Inf.codeToThir("val Fn = (v: int): int; %s".formatted(expression)));
    Assertions.assertEquals("Lambda parameter count or variadic shape does not match expected function type", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = (v: bool) => v.missing; f",
    "val Factory = (): Fn; val factory: Factory = () => ((v: bool) => v.missing); factory",
    "val apply = (x: int, fn: Fn) => fn(x); apply(...(5,), (v: bool) => v.missing)"
  })
  void given__invalid_annotation_and_dependent_body__when__the_pipeline_runs__then__parameter_diagnostic_is_preserved(
    final String expression
  ) {
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class,
      () -> Inf.codeToThir("val Fn = (v: int): int; %s".formatted(expression)));
    Assertions.assertTrue(error.getMessage().contains("Lambda parameter type does not match expected function type"));
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    return root;
  }
}
