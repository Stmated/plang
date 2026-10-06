package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class FindResultExpressionsVisitorTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "(v: int) => v",
    "{ val ignored = (other: int) => other; (v: int) => v; }",
    "{ { (v: int) => v; }; }"
  })
  void given__function_result__when__found__then__only_the_value_producing_lambda_is_returned(final String expression) {
    final var root = prepare("val result = %s; result".formatted(expression));
    final var value = assignment(root, "result").rhs();
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    final var results = FindResultExpressionsVisitor.find(value);
    Assertions.assertEquals(1, results.size());
    final var function = Assertions.assertInstanceOf(Hir.Function.class, results.getFirst());
    Assertions.assertAll(
      () -> Assertions.assertEquals("v", function.signature().parameters()[0].lexeme().name()),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "if (true) then ((a: int) => a) else ((b: int) => b)",
    "if (predicate((ignored: int) => ignored)) then ((a: int) => a) else ((b: int) => b)"
  })
  void given__conditional_result__when__found__then__both_branches_but_not_the_predicate_are_returned(final String expression) {
    final var root = prepare("""
      val Fn = (v: int): int;
      val predicate = (fn: Fn) => true;
      val result = %s;
      result
      """.formatted(expression));
    Assertions.assertEquals(List.of("a", "b"), parameterNames(FindResultExpressionsVisitor.find(assignment(root, "result").rhs())));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "apply((ignored: int) => ignored)",
    "{ apply((ignored: int) => ignored); }"
  })
  void given__call_result__when__found__then__the_call_is_returned_without_entering_its_arguments(final String expression) {
    final var root = prepare("""
      val Fn = (v: int): int;
      val apply = (fn: Fn) => fn(5);
      val result = %s;
      result
      """.formatted(expression));
    final var results = FindResultExpressionsVisitor.find(assignment(root, "result").rhs());
    Assertions.assertEquals(1, results.size());
    Assertions.assertInstanceOf(Hir.Call.class, results.getFirst());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{ if (true) then { return ((a: int) => a); }; (b: int) => b; }",
    "{ val nested = () => { return ((ignored: int) => ignored); }; return ((a: int) => a); return ((b: int) => b); }"
  })
  void given__function_returns__when__found__then__explicit_and_implicit_results_exclude_nested_function_returns(
    final String body
  ) {
    final var root = prepare("val make = () => %s; make".formatted(body));
    final var function = Assertions.assertInstanceOf(Hir.Function.class, assignment(root, "make").rhs());
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    Assertions.assertAll(
      () -> Assertions.assertEquals(List.of("a", "b"),
        parameterNames(FindResultExpressionsVisitor.findReturns(function.body())).stream().sorted().toList()),
      () -> Assertions.assertEquals(before, printer.render(root))
    );
  }

  private static List<String> parameterNames(final List<Hir.Expression> results) {
    return results.stream().map(result -> Assertions.assertInstanceOf(Hir.Function.class, result))
      .map(function -> function.signature().parameters()[0].lexeme().name()).toList();
  }

  private static Hir.Assignment assignment(final Hir.Expression root, final String name) {
    final var assignments = new ArrayList<Hir.Assignment>();
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment expression) {
        if (expression.lhs() instanceof Hir.Dec declaration && name.equals(declaration.lexeme().name())) {
          assignments.add(expression);
        }
        HirVisitor.super.visitAssignment(expression);
      }
    });
    Assertions.assertEquals(1, assignments.size());
    return assignments.getFirst();
  }

  private static Hir.Expression prepare(final String code) {
    final var root = HirTyIdentifierToTyTransformerPass.pass(Inf.codeToHir(code), new MachineTarget(64));
    HirIdentifierResolverVisitorPass.pass(root, Hir.Identifier::target);
    HirTyCommonVisitorPass.resolveAvailableTypes(root);
    return root;
  }
}
