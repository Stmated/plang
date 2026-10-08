package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HirFunctionValidationVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "apply(...({ return true; args; }))",
    "apply(...(make({ return true; })))",
    "apply(...(if ({ return true; }) then args else args))",
    "apply(...({ return true; args; }), fn = (v) => v)"
  })
  void given__noncontinuing_spread_with_an_incompatible_function_slot__when__validated__then__the_slot_is_still_checked(
    final String call
  ) {
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir("""
      val Fn = (value: uint8): uint8;
      val wrong = (value: uint8): bool => false;
      val args = (fn = wrong, x = 1u8);
      val make = (flag: bool) => args;
      val apply = (x: uint8, fn: Fn) => fn(x);
      val use = () => %s;
      use()
      """.formatted(call)));
    Assertions.assertTrue(error.getMessage().contains("Function type does not match expected function type"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "new heap S { flag = { return true; }; fn = (v) => false; }",
    "new heap ({ return true; S; }) { fn = (v) => false; flag = false; }",
    "new heap (if ({ return true; }) then S else S) { fn = (v) => false; flag = false; }",
    "({ return true; s; }).fn = (v) => false"
  })
  void given__incompatible_function_field_after_transfer__when__validated__then__resolved_member_constraint_is_reported(
    final String expression
  ) {
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir("""
      val Fn = (value: uint8): uint8;
      val S = struct { val fn: Fn; val flag: bool; };
      val s = new heap S { fn = (v) => v; flag = false; };
      val use = () => { %s; };
      use()
      """.formatted(expression)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "({ return true; apply; })((v) => false)",
    "apply({ return true; (v) => false; })",
    "(factory({ return true; }))((v) => false)",
    "({ return true; apply; })(fn = (v) => false)"
  })
  void given__noncontinuing_function_use__when__the_pipeline_validates__then__nominal_signatures_still_report_mismatches(
    final String invocation
  ) {
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir("""
      val Fn = (value: uint8): uint8;
      val Consumer = (fn: Fn): uint8;
      val apply: Consumer = (fn) => fn(1);
      val factory = (flag: bool): Consumer => apply;
      val use = () => %s;
      use()
      """.formatted(invocation)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = () => 1; f",
    "val f: Fn = (a, b) => 1; f",
    "val f: Fn = (v, ...) => v; f",
    "val f: Fn = (v: bool) => 1; f",
    "val f: Fn = (v) => true; f",
    "val f: Fn = { (v) => true; }; f",
    "val f: Fn = { return 1; (v) => true; }; f",
    "val f: Fn = if (true) then ((v) => v) else ((v) => true); f",
    "val S = struct { val fn: Fn; }; new heap S { fn = (v) => true; }",
    "val apply = (fn: Fn) => fn(5); apply((v) => true)",
    "val apply = (x: int, fn: Fn) => fn(x); apply(fn = (v) => true, x = 5)",
    "val Factory = (): Fn; val factory: Factory = () => ((v) => true); factory"
  })
  void given__incompatible_typed_lambda__when__use_site_is_validated__then__rejected_without_rewriting(
    final String expression
  ) {
    final var root = prepare("val Fn = (v: int): int; %s".formatted(expression));
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class,
      () -> HirFunctionValidationVisitorPass.pass(root));
    Assertions.assertAll(
      () -> Assertions.assertTrue(error.getMessage().contains("Function type does not match expected function type")),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = wrong; f",
    "val S = struct { val fn: Fn; }; new heap S { fn = wrong; }",
    "val apply = (fn: Fn) => fn(5); apply(wrong)",
    "val apply = (fn: Fn, x: int) => fn(x); val args = (fn = wrong, x = 5); apply(...args)",
    "val make = (): Fn => wrong; make()",
    "val make = (): Fn => { return wrong; }; make()",
    "val make = (): Fn => true; make()",
    "val make = (): Fn => { return true; }; make()"
  })
  void given__incompatible_function_value__when__typed_use_site_is_validated__then__no_lambda_context_is_needed(
    final String expression
  ) {
    final var root = prepare("val Fn = (v: int): int; val wrong = (v: int) => true; %s".formatted(expression));
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertAll(
      () -> Assertions.assertThrows(InvalidTypeConversionException.class,
        () -> HirFunctionValidationVisitorPass.pass(root)),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = fn; f",
    "val f: Fn = { fn; }; f",
    "val f: Fn = if (true) then fn else ((v) => v); f",
    "val S = struct { val fn: Fn; }; new heap S { fn = fn; }",
    "val apply = (fn: Fn, x: int) => fn(x); apply(x = 5, fn = fn)",
    "val apply = (fn: Fn, x: int) => fn(x); val args = (fn = fn, x = 5); apply(...args)",
    "val make = (): Fn => fn; make()",
    "val make = (): Fn => { return fn; }; make()",
    "val make = (): Fn => { val nested = (): bool => true; return fn; }; make()"
  })
  void given__compatible_function_values__when__validated__then__types_and_tree_are_unchanged(final String expression) {
    final var root = prepare("val Fn = (value: int): int; val fn = (v: int) => v; %s".formatted(expression));
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertDoesNotThrow(() -> HirFunctionValidationVisitorPass.pass(root));
    Assertions.assertEquals(before, printer.render(root));
  }

  @Test
  void given__missing_parameter_annotation__when__final_validation_runs_alone__then__it_does_not_infer_or_resolve_types() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val Fn = (v: int): int; val f: Fn = (v) => v; f"), new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertAll(
      () -> Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirFunctionValidationVisitorPass.pass(root)),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @Test
  void given__unknown_function_field__when__typed_initializer_is_validated__then__field_error_is_reported() {
    final var root = prepare("""
      val Fn = (v: int): int;
      val S = struct { val fn: Fn; };
      new heap S { missing = (v: int) => v; }
      """);
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> HirFunctionValidationVisitorPass.pass(root));
    Assertions.assertEquals("Unknown struct field: missing", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val x: uint8 = 1; x",
    "val fn = (): uint8 => 1; fn()",
    "val fn = (): uint8 => { return 1; }; fn()"
  })
  void given__scalar_conversion__when__function_compatibility_is_validated__then__unrelated_typing_is_left_unchanged(
    final String code
  ) {
    final var root = prepare(code);
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertDoesNotThrow(() -> HirFunctionValidationVisitorPass.pass(root));
    Assertions.assertEquals(before, printer.render(root));
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    return root;
  }
}
