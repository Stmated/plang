package org.inf.hir.raising;

import org.inf.Inf;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AstJuxtapositionToHirTest {

  @ParameterizedTest
  @CsvSource(value = {
    "f x | f(x)",
    "f x, y | f(x, y)",
    "~(f x) | ~(f(x))",
    "~(service.call x) | ~(service.call(x))",
    "f g x, y | f(g(x, y))",
    "outer(inner x, y) | outer(inner(x, y))",
    "outer(inner(x), y) | outer(inner(x), y)",
    "outer((inner x), y) | outer(inner(x), y)",
    "outer(inner middle x, y) | outer(inner(middle(x, y)))",
    "f x + y, z * 2 | f(x + y, z * 2)",
    "service.call x, y | service.call(x, y)",
    "service.child.call x, y | service.child.call(x, y)",
    "val result = f x, y | val result = f(x, y)",
    "return f x | return f(x)",
    "{ f x } | { f(x) }",
    "val fn = (x: int) => f x | val fn = (x: int) => f(x)",
    "f x; g y | f(x); g(y)",
    "if (true) then (f x) else (g y) | if (true) f(x) else g(y)",
    "if (f x) { g y } | if (f(x)) { g(y) }",
    "f x, (a: 1, b: 2) | f(x, (a: 1, b: 2))",
    "f x, (y, z) | f(x, (y, z))",
    "f x, (y,) | f(x, (y,))",
    "outer inner x, (y, z) | outer(inner(x, (y, z)))",
    "f tuple | f(tuple)",
    "outer inner x, y | outer(inner(x, y))",
  }, delimiter = '|')
  void given__implicit_calls__when__raised__then__match_explicit_hir(
    final String implicit, final String explicit
  ) {
    final var printer = new ToStringTreeHirVisitor();
    final var actual = printer.render(Inf.codeToHir(implicit));
    Assertions.assertEquals(printer.render(Inf.codeToHir(explicit)), actual);
  }
}
