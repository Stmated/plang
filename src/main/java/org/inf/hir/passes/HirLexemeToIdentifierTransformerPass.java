package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;

@UtilityClass
public class HirLexemeToIdentifierTransformerPass {

  public static Hir.Expression pass(Hir.Expression expr) {

    final var transformer = new Transformer();
    return expr.transform(transformer);
  }

  private static class Transformer implements HirTransformer {

    @Override
    public Hir.Expression transformLexeme(Hir.Lexeme expr) {

      return new Hir.Identifier(expr.name(), null);
    }

    @Override
    public Hir.Lexeme transformParameterName(Hir.Lexeme expr) {
      return expr;
    }

    @Override
    public Hir.Lexeme transformDecName(Hir.Lexeme expr) {
      return expr;
    }

    @Override
    public Hir.Assignment[] transformNewByBlockFields(final Hir.Assignment[] fields) {
      for (final var field : fields) {
        // Only the direct LHS is a field name; assignments in the RHS still resolve normally.
        field.rhs(field.rhs().transform(this));
      }
      return fields;
    }
  }
}
