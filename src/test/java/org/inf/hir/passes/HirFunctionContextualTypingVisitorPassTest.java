package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.TyFn;
import org.inf.ty.Ty;
import org.inf.ty.TyParam;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirFunctionContextualTypingVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "apply(...({ return true; args; }), fn = (v) => v)",
    "apply(...({ return true; }), fn = (v) => v)",
    "apply(...({ return true; },), fn = (v) => v)",
    "apply(...(make({ return true; })), fn = (v) => v)"
  })
  void given__noncontinuing_spread__when__other_arguments_are_contextualized__then__binding_uses_available_slots(
    final String call
  ) {
    final var root = Inf.codeToThir("""
      val Fn = (value: uint8): uint8;
      val args = (x = 1u8,);
      val make = (flag: bool): (x: uint8,) => args;
      val apply = (x: uint8, fn: Fn) => fn(x);
      val use = () => %s;
      use()
      """.formatted(call)).root();
    final var parameters = new ArrayList<Hir.Parameter>();
    root.visit(new HirVisitor() {
      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("v")) {
          parameters.add(parameter);
        }
        HirVisitor.super.visitParameter(parameter);
      }
    });
    Assertions.assertEquals(1, parameters.size());
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INFER, parameters.getFirst().typeAnnotation().ty()),
      () -> Assertions.assertEquals(Tys.fromString("uint8", new MachineTarget(64)), parameters.getFirst().resolvedTy()),
      () -> Assertions.assertEquals(Ty.BOOLEAN, root.ty())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "new heap S { fn = (v) => v; flag = false; }",
    "new heap S { flag = { return true; }; fn = (v) => v; }",
    "new heap ({ return true; S; }) { fn = (v) => v; flag = false; }",
    "new heap (if ({ return true; }) then S else S) { fn = (v) => v; flag = false; }"
  })
  void given__construction_layout__when__lambda_fields_are_contextualized__then__transfers_do_not_hide_the_signature(
    final String construction
  ) {
    final var root = Inf.codeToThir("""
      val Fn = (value: uint8): uint8;
      val S = struct { val fn: Fn; val flag: bool; };
      val use = () => %s;
      use()
      """.formatted(construction)).root();
    final var parameters = new ArrayList<Hir.Parameter>();
    root.visit(new HirVisitor() {
      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("v")) {
          parameters.add(parameter);
        }
        HirVisitor.super.visitParameter(parameter);
      }
    });
    Assertions.assertEquals(1, parameters.size());
    final var parameter = parameters.getFirst();
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INFER, parameter.typeAnnotation().ty()),
      () -> Assertions.assertEquals(Tys.fromString("uint8", new MachineTarget(64)), parameter.resolvedTy())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val Fn = (value: uint8): uint8; val f: Fn = (p) => p; f",
    "val Fn = (value: (uint8, bool)): uint8; val f: Fn = (p) => p[0]; f",
    "val Fn = (value: uint8): uint8; val Consumer = (callback: Fn): uint8; val f: Consumer = (p) => p(1); f",
    "val Fn = (value: uint8): uint8; val f = (p: Fn) => p(1); f",
    "val S = struct { val value: uint8; }; val f = (p: S) => p.value; f"
  })
  void given__inferred_or_alias_parameter_annotation__when__the_pipeline_runs__then__source_annotation_is_preserved(
    final String code
  ) {
    final var hir = Inf.codeToHir(code);
    final var parameters = new ArrayList<Hir.Parameter>();
    hir.visit(new HirVisitor() {
      @Override
      public void visitParameter(final Hir.Parameter parameter) {
        if (parameter.lexeme().name().equals("p")) {
          parameters.add(parameter);
        }
        HirVisitor.super.visitParameter(parameter);
      }
    });
    Assertions.assertEquals(1, parameters.size());
    final var parameter = parameters.getFirst();
    final var annotation = parameter.typeAnnotation();
    final var root = new HirToThirRaising(new MachineTarget(64)).raise(hir).root();
    final var signature = Assertions.assertInstanceOf(TyFn.class, root.ty());
    Assertions.assertAll(
      () -> Assertions.assertSame(annotation.expression(), parameter.typeAnnotation().expression()),
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertFalse(Tys.containsInferred(parameter.resolvedTy())),
      () -> Assertions.assertEquals(parameter.resolvedTy(), signature.parameters()[0].ty())
    );
    if (annotation.expression() == null) {
      Assertions.assertEquals(Ty.INFER, annotation.ty());
    } else {
      Assertions.assertInstanceOf(Hir.Identifier.class, parameter.typeAnnotation().expression());
      Assertions.assertSame(parameter.typeAnnotation().ty(), parameter.resolvedTy());
    }
  }

  @Test
  void given__inferred_declaration__when__lambda_is_contextualized__then__previous_binding_is_not_an_initializer_constraint() {
    final var root = Inf.codeToHir("val f = (v) => v; f");
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitDec(final Hir.Dec declaration) {
        declaration.resolvedTy(new TyFn(new TyParam[]{new TyParam("v", Ty.BOOLEAN)}, false, Ty.BOOLEAN));
      }

      @Override
      public void visitFunction(final Hir.Function function) {
        functions.add(function);
        HirVisitor.super.visitFunction(function);
      }
    });
    HirFunctionContextualTypingVisitorPass.pass(root);
    Assertions.assertEquals(1, functions.size());
    final var parameter = functions.getFirst().signature().parameters()[0];
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INFER, parameter.typeAnnotation().ty()),
      () -> Assertions.assertNull(parameter.resolvedTy())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = (v) => v * 2; f(5)",
    "var f: Fn = (v) => v; f = (v) => v * 2; f(5)",
    "val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn(5)",
    "val S = struct { val fn: Fn; }; val s = new heap S { fn = (v: int) => v; }; s.fn = (v) => v * 2; s.fn(5)",
    "val apply = (fn: Fn, x: int) => fn(x); apply((v) => v * 2, 5)",
    "val apply = (fn: Fn, x: int) => fn(x); apply(x = 5, fn = (v) => v * 2)",
    "val f: Fn = { (v) => v * 2 }; f(5)",
    "val f: Fn = if (true) then ((v) => v * 2) else ((v) => v); f(5)",
    "val Factory = (): Fn; val factory: Factory = () => ((v) => v * 2); (factory())(5)",
    "val Factory = (): Fn; val factory: Factory = () => { return ((v) => v * 2); }; (factory())(5)",
    "val make = (): Fn => { if (true) then { return ((v) => v * 2); }; (v) => v; }; (make())(5)",
    "val apply = (x: int, fn: Fn) => fn(x); apply(...(5,), fn = (v) => v * 2)",
    "val apply = (x: int, fn: Fn) => fn(x); apply(fn = (v) => v * 2, ...(5,))"
  })
  void given__expected_function_type__when__lambda_is_typed__then__missing_parameter_types_are_inferred(final String expression) {
    Assertions.assertDoesNotThrow(() -> Inf.codeToThir("val Fn = (value: int): int; %s".formatted(expression)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "uint8 | v",
    "bool | v"
  })
  void given__nondefault_parameter_type__when__inferred__then__type_and_lambda_parameter_name_are_preserved(
    final String type, final String body
  ) {
    final var root = Inf.codeToThir("val Fn = (value: %s): %s; val f: Fn = (v) => %s; f".formatted(type, type, body)).root();
    final var function = Assertions.assertInstanceOf(TyFn.class, root.ty());
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function expression) {
        functions.add(expression);
        HirVisitor.super.visitFunction(expression);
      }
    });
    Assertions.assertEquals(1, functions.size());
    final var parameter = functions.getFirst().signature().parameters()[0];
    Assertions.assertAll(
      () -> Assertions.assertEquals("v", parameter.lexeme().name()),
      () -> Assertions.assertEquals(Ty.VOID, parameter.ty()),
      () -> Assertions.assertEquals(Ty.INFER, parameter.typeAnnotation().ty()),
      () -> Assertions.assertEquals(function.parameters()[0].ty(), parameter.resolvedTy()),
      () -> Assertions.assertEquals(function.returnTy(), parameter.resolvedTy())
    );
  }

  @Test
  void given__tuple_parameter__when__lambda_is_contextualized__then__aggregate_parameter_is_inferred() {
    final var code = """
      val Fn = (value: (int, bool)): int;
      val f: Fn = (v) => v[0];
      f((5, true))
      """;
    Assertions.assertDoesNotThrow(() -> Inf.codeToMir(code));
  }

  @Test
  void given__partially_annotated_lambda__when__contextualized__then__only_missing_parameter_types_are_supplied() {
    final var root = Inf.codeToThir("""
      val Fn = (value: int, flag: bool): int;
      val f: Fn = (x, enabled: bool) => if (enabled) then x else 0;
      f
      """).root();
    final var function = Assertions.assertInstanceOf(TyFn.class, root.ty());
    Assertions.assertAll(
      () -> Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)), function.parameters()[0].ty()),
      () -> Assertions.assertEquals(Tys.fromString("bool", new MachineTarget(64)), function.parameters()[1].ty())
    );
  }

  @Test
  void given__no_expected_function_type__when__lambda_is_typed__then__unresolved_parameter_is_rejected() {
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir("val f = (v) => v; f"));
    Assertions.assertTrue(error.getMessage().contains("Function signature requires resolved parameter and return types"));
  }

  @Test
  void given__repeated_contextual_typing__when__parameter_types_are_resolved__then__result_is_stable() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn(5)"),
      new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    HirFunctionParameterValidationVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    HirFunctionValidationVisitorPass.pass(root);
    final var expected = root.ty();
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    HirTyCommonVisitorPass.pass(root);
    HirFunctionValidationVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)), expected),
      () -> Assertions.assertEquals(expected, root.ty())
    );
  }

  @Test
  void given__standalone_lambda_pass__when__run__then__parameters_are_inferred_without_retyping_tuple_values() {
    final var root = prepare("""
      val Fn = (value: int): int;
      val S = struct { val fn: Fn; };
      val s = new heap S { fn = (v) => v * 2; };
      val tuple: (uint8,) = (1,);
      s.fn(5)
      """);
    final var functions = new ArrayList<Hir.Function>();
    final var tuples = new ArrayList<Hir.Tuple>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function expression) {
        functions.add(expression);
        HirVisitor.super.visitFunction(expression);
      }

      @Override
      public void visitTuple(final Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }
    });
    Assertions.assertEquals(1, functions.size());
    final var beforeRootType = root.ty();
    final var beforeTupleTypes = tuples.stream().map(Hir.Tuple::ty).toList();
    HirFunctionContextualTypingVisitorPass.pass(root);
    final var parameter = functions.getFirst().signature().parameters()[0];
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.INFER, parameter.typeAnnotation().ty()),
      () -> Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)), parameter.resolvedTy()),
      () -> Assertions.assertEquals(beforeRootType, root.ty()),
      () -> Assertions.assertEquals(beforeTupleTypes, tuples.stream().map(Hir.Tuple::ty).toList()),
      () -> Assertions.assertTrue(tuples.stream().allMatch(tuple -> tuple.contextualType() == null)),
      () -> Assertions.assertEquals(Ty.INTEGER, tuples.getLast().children()[0].value().ty())
    );
  }

  @Test
  void given__incompatible_lambda_return__when__only_inference_runs__then__return_type_is_not_validated() {
    final var root = prepare("val Fn = (value: int): int; val f: Fn = (v) => true; f");
    Assertions.assertDoesNotThrow(() -> HirFunctionContextualTypingVisitorPass.pass(root));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f: Fn = (v: bool) => 1; f",
    "val f: Fn = () => 1; f"
  })
  void given__incompatible_lambda_declaration__when__only_inference_runs__then__validation_is_left_to_its_own_pass(
    final String expression
  ) {
    final var root = prepare("val Fn = (value: int): int; %s".formatted(expression));
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertDoesNotThrow(() -> HirFunctionContextualTypingVisitorPass.pass(root));
    Assertions.assertEquals(before, printer.render(root));
  }

  @Test
  void given__general_types_are_not_prepared__when__lambda_pass_runs__then__it_does_not_resolve_aliases_or_receivers() {
    final var root = HirTyIdentifierToTyTransformerPass.pass(
      Inf.codeToHir("val Fn = (value: int): int; val S = struct { val fn: Fn; }; new heap S { fn = (v) => v; }"),
      new MachineTarget(64)
    );
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    Assertions.assertEquals(before, printer.render(root));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val result: Fn = { (ignored) => ignored; (linked) => linked; }; result",
    "val result = (): Fn => { val nested = () => { return ((ignored) => ignored); }; return ((linked) => linked); }; result"
  })
  void given__unrelated_lambdas_near_a_result__when__use_sites_are_linked__then__only_result_parameters_are_inferred(
    final String expression
  ) {
    final var root = prepare("val Fn = (v: int): int; %s".formatted(expression));
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function function) {
        if (function.signature().parameters().length != 0) {
          functions.add(function);
        }
        HirVisitor.super.visitFunction(function);
      }
    });
    Assertions.assertEquals(2, functions.size());
    HirFunctionContextualTypingVisitorPass.pass(root);
    Assertions.assertAll(
      () -> Assertions.assertTrue(Tys.isInferred(functions.getFirst().signature().parameters()[0].typeAnnotation().ty())),
      () -> Assertions.assertTrue(Tys.isInferred(functions.getFirst().signature().parameters()[0].resolvedTy())),
      () -> Assertions.assertEquals(Ty.INFER, functions.getLast().signature().parameters()[0].typeAnnotation().ty()),
      () -> Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)),
        functions.getLast().signature().parameters()[0].resolvedTy())
    );
  }

  @Test
  void given__no_resolved_parameter_constraint__when__use_site_is_linked__then__the_signature_is_not_invalidated() {
    final var root = prepare("val f = (v) => v; f");
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    HirFunctionContextualTypingVisitorPass.pass(root);
    Assertions.assertEquals(before, printer.render(root));
  }

  @Test
  void given__unavailable_constructor_type__when__use_sites_are_visited__then__independently_typed_children_are_still_linked() {
    final var root = prepare("""
      val Fn = (value: int): int;
      val S = struct { val fn: Fn; };
      val apply = (fn: Fn) => fn(5);
      new heap S { fn = { apply((linked) => linked); (v: int) => v; }; }
      """);
    final var constructors = new ArrayList<Hir.NewByBlock>();
    final var functions = new ArrayList<Hir.Function>();
    root.visit(new HirVisitor() {
      @Override
      public void visitNewByBlock(final Hir.NewByBlock expression) {
        constructors.add(expression);
        HirVisitor.super.visitNewByBlock(expression);
      }

      @Override
      public void visitFunction(final Hir.Function expression) {
        if (expression.signature().parameters()[0].lexeme().name().equals("linked")) {
          functions.add(expression);
        }
        HirVisitor.super.visitFunction(expression);
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, constructors.size()),
      () -> Assertions.assertEquals(1, functions.size())
    );
    final var target = Assertions.assertInstanceOf(Hir.Identifier.class, constructors.getFirst().target());
    final var declaration = Assertions.assertInstanceOf(Hir.Dec.class, target.target());
    declaration.resolvedTy(null);
    Assertions.assertNull(Tys.getConstructionTargetTy(target));
    HirFunctionContextualTypingVisitorPass.pass(root);
    Assertions.assertEquals(Tys.fromString("int", new MachineTarget(64)),
      functions.getFirst().signature().parameters()[0].resolvedTy(),
      () -> new ToStringTreeHirVisitor().render(root));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val apply: Apply = (use) => use((v) => v); apply(consumer)",
    "val S = struct { val fn: Apply; }; val s = new heap S { fn = (use) => use((v) => v); }; s.fn(consumer)",
    "val make = (): Apply => ((use) => use((v) => v)); (make())(consumer)",
    "val invoke = (fn: Apply) => fn(consumer); invoke((use) => use((v) => v))"
  })
  void given__function_parameter_supplies_a_nested_callback_signature__when__use_sites_are_linked__then__parameter_links_precede_body_visits(
    final String expression
  ) {
    final var code = """
      val Fn = (value: int): int;
      val Consumer = (fn: Fn): int;
      val Apply = (use: Consumer): int;
      val consumer = (fn: Fn) => fn(5);
      %s
      """.formatted(expression);
    Assertions.assertDoesNotThrow(() -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val Fn = (value: (uint8,)): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v[0] + 0; }; s.fn((1,))",
    "val Fn = (value: (uint8,)): int; val apply = (fn: Fn, value: (uint8,)) => fn(value); apply((v) => v[0] + 0, (1,))",
    "val Fn = (value: int): int; val S = struct { val fn: Fn; }; val make = () => new heap S { fn = (v) => v; }; make().fn = (v) => v * 2; make().fn(5)"
  })
  void given__lambda_and_tuple_typing_interactions__when__independent_passes_run__then__typing_order_is_preserved(final String code) {
    Assertions.assertDoesNotThrow(() -> Inf.codeToMir(code));
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    return root;
  }
}
