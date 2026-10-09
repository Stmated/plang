package org.inf.hir;

import org.inf.Inf;
import org.inf.hir.passes.HirTyCommonVisitorPass;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HirCallableSignatureVisitorTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val f = () => 7; if ({ return true; }) then f else f | function",
    "val f = () => 7; if ({ return true; }) then f else { return f; } | function",
    "val f = () => 7; if ({ return true; }) then f else true | boolean",
    "val f = () => 7; if ({ return true; }) then f | missing_else",
    "val f = () => 7; val noValue = () => { val n = 7; }; "
      + "if ({ return true; }) then noValue() else f | void_then_function",
    "val f = () => 7; if ({ return true; }) then { return f; } else { return f; } | no_value"
  })
  void given__source_conditional_branches__when__queried__then__nominal_callable_information_and_missing_arms_are_preserved(
    final String code, final String resultKind
  ) {
    final var root = Inf.codeToThir(code).root();
    final var conditional = nodes(root, Hir.Conditional.class).getFirst();
    final var function = nodes(root, Hir.Function.class).getFirst().ty();
    final Ty expected = switch (resultKind) {
      case "function" -> function;
      case "boolean" -> Tys.union(function, Ty.BOOLEAN);
      case "missing_else" -> Tys.union(function, Ty.VOID);
      case "void_then_function" -> Tys.union(Ty.VOID, function);
      case "no_value" -> null;
      default -> throw new AssertionError("Unknown result kind");
    };
    final var printer = new ToStringTreeHirVisitor();
    final var before = printer.render(root);
    assertAll(
      () -> assertEquals(expected, HirCallableSignatureVisitor.find(conditional)),
      () -> assertEquals(expected, Tys.getCallableValueTy(conditional)),
      () -> assertEquals(expected instanceof TyFn ? expected : null, Tys.getCallableSignature(conditional)),
      () -> assertSame(Ty.DEADEND, conditional.ty()),
      () -> assertEquals(before, printer.render(root))
    );
  }

  @Test
  void given__source_conditional_callee__when__function_body_is_retyped__then__the_latest_signature_is_selected() {
    final var root = Inf.codeToThir("val f = () => 7; if ({ return true; }) then f else f").root();
    final var function = nodes(root, Hir.Function.class).getFirst();
    final var conditional = nodes(root, Hir.Conditional.class).getFirst();
    final var original = function.ty();
    final var replacement = nodes(Inf.codeToThir("val f = () => true; f").root(), Hir.Function.class).getFirst();
    assertSame(original, Tys.getCallableSignature(conditional));

    function.body(replacement.body());
    HirTyCommonVisitorPass.pass(root);

    final var refined = function.ty();
    assertAll(
      () -> assertNotEquals(original, refined),
      () -> assertSame(Ty.BOOLEAN, refined.returnTy()),
      () -> assertSame(refined, Tys.getCallableSignature(conditional)),
      () -> assertSame(refined, Tys.getCallableValueTy(conditional)),
      () -> assertSame(Ty.DEADEND, conditional.ty())
    );
  }

  @Test
  void given__source_callable_parameter_reference__when__queried__then__the_actual_parameter_binding_is_read() {
    final var root = Inf.codeToThir("val Fn = (): int; val use = (callback: Fn) => callback(); use").root();
    final var parameter = nodes(root, Hir.Parameter.class).stream()
      .filter(candidate -> candidate.lexeme().name().equals("callback")).findFirst().orElseThrow();
    final var reference = nodes(root, Hir.Identifier.class).stream()
      .filter(identifier -> identifier.target() == parameter).findFirst().orElseThrow();
    assertAll(
      () -> assertSame(parameter, reference.target()),
      () -> assertSame(parameter.resolvedTy(), HirCallableSignatureVisitor.find(reference)),
      () -> assertSame(parameter.resolvedTy(), Tys.getCallableSignature(parameter))
    );
  }

  private static <T extends Hir.Expression> List<T> nodes(final Hir.Expression root, final Class<T> nodeType) {
    final var found = new ArrayList<T>();
    root.visit(new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        if (nodeType.isInstance(expression)) {
          found.add(nodeType.cast(expression));
        }
        HirVisitor.super.visitChild(expression);
      }
    });
    assertFalse(found.isEmpty(), () -> "Missing source node: " + nodeType.getSimpleName());
    return found;
  }
}
