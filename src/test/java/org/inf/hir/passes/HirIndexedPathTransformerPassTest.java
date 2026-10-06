package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.hir.util.ToStringTreeHirVisitor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HirIndexedPathTransformerPassTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "s.value[0] | (s.value)[0]",
    "s.value[0][1] | (s.value)[0][1]",
    "s.values[index][0] | (s.values)[index][0]",
    "s.inner.value[0] | (s.inner.value)[0]",
    "s.values[index].value[0] | ((s.values)[index].value)[0]",
    "s.values[index][0] = t | (s.values)[index][0] = t"
  })
  void given__indexed_field_path__when__normalized__then__indexing_targets_the_complete_field(
    String code, String equivalent
  ) {
    final var printer = new ToStringTreeHirVisitor();
    final var hir = Inf.codeToHir(code);
    assertEquals(printer.render(Inf.codeToHir(equivalent)), printer.render(hir));
    assertEquals(printer.render(hir), printer.render(HirIndexedPathTransformerPass.pass(hir)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "s.call() | (s.call)()",
    "s.call(1, 2) | (s.call)(1, 2)",
    "s.call(b = value, a = 1) | (s.call)(b = value, a = 1)",
    "s.call a = 1, b = value | (s.call)(a = 1, b = value)",
    "s.call(...args) | (s.call)(...args)",
    "s.inner.call(1) | (s.inner.call)(1)",
    "s.values[index].call(value) | ((s.values)[index].call)(value)",
    "(s.calls[index])(value) | ((s.calls)[index])(value)",
    "s.make().call(value) | ((s.make)().call)(value)",
    "(s.make())(value) | ((s.make)())(value)",
    "s.make().values[index] | ((s.make)().values)[index]"
  })
  void given__called_field_path__when__normalized__then__only_the_target_uses_the_complete_field_path(
    final String code, final String equivalent
  ) {
    final var printer = new ToStringTreeHirVisitor();
    final var hir = Inf.codeToHir(code);
    assertEquals(printer.render(Inf.codeToHir(equivalent)), printer.render(hir));
    assertEquals(printer.render(hir), printer.render(HirIndexedPathTransformerPass.pass(hir)));
  }
}
