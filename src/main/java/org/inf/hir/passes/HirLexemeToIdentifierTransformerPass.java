package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;

import java.util.ArrayList;

@UtilityClass
public class HirLexemeToIdentifierTransformerPass {

  public static Hir.Expression pass(Hir.Expression expr) {

    final var transformer = new Transformer();
    return expr.transform(transformer);
  }

  private static class Transformer implements HirTransformer {

    private int depth = 0;

    @Override
    public Hir.Expression transformLexeme(Hir.Lexeme expr) {

      if (depth == 0) {
        return new Hir.Identifier(expr, null);
      } else {
        return HirTransformer.super.transformLexeme(expr);
      }
    }

    /**
     * Remove this, instead the node for assigning inside a constructor block should be its own node type even if syntax is the same.
     * Then IN THEORY we could create code that cannot be written in code but represented in our tree.
     */
    private int newCounter = 0;

    @Override
    public Hir.Assignment[] transformNewByBlockFields(Hir.Assignment[] fields) {
      try {
        newCounter++;
        return HirTransformer.super.transformNewByBlockFields(fields);
      } finally {
        newCounter--;
      }
    }

    @Override
    public Hir.Expression transformAssignmentLhs(Hir.Expression expr) {
      if (newCounter > 0) {
        try {
          depth++;
          return HirTransformer.super.transformAssignmentLhs(expr);
        } finally {
          depth--;
        }
      } else {
        return HirTransformer.super.transformAssignmentLhs(expr);
      }
    }

    @Override
    public Hir.Lexeme transformParameterName(Hir.Lexeme expr) {
      try {
        depth++;
        return HirTransformer.super.transformParameterName(expr);
      } finally {
        depth--;
      }
    }

    @Override
    public Hir.Lexeme transformDecName(Hir.Lexeme expr) {
      try {
        depth++;
        return HirTransformer.super.transformDecName(expr);
      } finally {
        depth--;
      }
    }

    @Override
    public Hir.Expression transformPath(Hir.Path expr) {

      final var length = expr.elements().length;
      try {

        final var list = new ArrayList<Hir.Expression>();
        for (var i = 0; i < length; i++) {
          final var transformed = expr.elements()[i].transform(this);
          if (transformed != null) {
            list.add(transformed);
          }

          depth++;
        }

        expr.elements(list.toArray(new Hir.Expression[0]));
        return expr;

      } finally {
        depth -= length;
      }
    }
  }
}
