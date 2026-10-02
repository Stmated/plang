package org.inf.ast;

import org.inf.Inf;
import org.inf.ast.util.ToStringTreeAstVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AstTransformerTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "(x)",

    "(x,)",

    "(x, y)",

    "(x, y,)",

    "((x,), ((y, z),))"
  })
  void given__comma_nodes__when__transforming_children__then__group_boundaries_are_preserved(final String code) {
    final var ast = Inf.codeToAst(code);
    final var printer = new ToStringTreeAstVisitor();
    final var before = printer.visit(ast);
    final var transformer = new AstTransformer() {
      @Override
      public Ast.Expression visitLexeme(final Ast.Lexeme expr) {
        return new Ast.Lexeme("renamed_" + expr.name());
      }
    };

    assertAll(
      () -> assertEquals(before.replace("(Lexeme \"", "(Lexeme \"renamed_"), printer.visit(transformer.visit(ast))),
      () -> assertEquals(before, printer.visit(ast))
    );
  }

  @Test
  void given__replacement_visitor__when__transforming__then__nested_replacements_reach_owners() {
    final var raw = Inf.codeToRawAst("if (p) then { f(x)[y] } else { g(z) }");
    final var printer = new ToStringTreeAstVisitor();
    final var before = printer.visit(raw);
    final var transformer = new AstTransformer() {
      @Override
      public Ast.Expression visitLexeme(final Ast.Lexeme expr) {
        return new Ast.Lexeme("renamed_" + expr.name());
      }
    };

    assertAll(
      () -> assertEquals(before.replace("(Lexeme \"", "(Lexeme \"renamed_"), printer.visit(transformer.visit(raw))),
      () -> assertEquals(before, printer.visit(raw))
    );
  }
}
