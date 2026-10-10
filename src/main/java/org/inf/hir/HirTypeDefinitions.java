package org.inf.hir;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/// Classifies source type definitions without deriving types from runtime values.
public final class HirTypeDefinitions {

  private final Map<Hir.Dec, Hir.Expression> definitions = new IdentityHashMap<>();
  private final Map<Hir.Expression, Boolean> typeSyntax = new IdentityHashMap<>();
  private final Map<Hir.Expression, Boolean> unionDefinitions = new IdentityHashMap<>();

  public HirTypeDefinitions(final Hir.Expression root) {
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment expr) {
        if (expr.lhs() instanceof Hir.Dec declaration) {
          definitions.put(declaration, expr.rhs());
        }
        HirVisitor.super.visitAssignment(expr);
      }
    });
  }

  public Hir.Expression definition(final Hir.Expression target) {
    return target instanceof Hir.Dec declaration ? definitions.get(declaration) : target;
  }

  public boolean isType(final Hir.Expression expr) {
    final var visitor = new TypeSyntaxVisitor();
    visitor.visitChild(expr);
    return visitor.valid;
  }

  public boolean isUnionDefinition(final Hir.Expression expr) {
    final var visitor = new HirVisitor() {
      private final Set<Hir.Expression> resolving = Collections.newSetFromMap(new IdentityHashMap<>());
      private boolean union;

      @Override
      public void visitChild(final Hir.Expression child) {
        final var cached = unionDefinitions.get(child);
        if (cached != null) {
          union = cached;
          return;
        }
        if ((child instanceof Hir.Identifier || child instanceof Hir.Union || child instanceof Hir.BinaryOperation)
          && resolving.add(child)) {
          child.visit(this);
          unionDefinitions.put(child, union);
        }
      }

      @Override
      public void visitIdentifier(final Hir.Identifier identifier) {
        visitChild(definition(identifier.target()));
      }

      @Override
      public void visitUnion(final Hir.Union expr) {
        union = true;
      }

      @Override
      public void visitBinaryOperation(final Hir.BinaryOperation expr) {
        union = expr.kind() == Hir.BinaryOperationKind.BIT_OR && isType(expr);
      }

    };
    visitor.visitChild(expr);
    return visitor.union;
  }

  private final class TypeSyntaxVisitor implements HirVisitor {

    private final Set<Hir.Expression> resolving = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean valid = true;

    @Override
    public void visitDec(final Hir.Dec expr) {
      visitDecType(expr.typeAnnotation());
    }

    @Override
    public void visitParameter(final Hir.Parameter expr) {
      visitParameterType(expr.typeAnnotation());
    }

    @Override
    public void visitChild(final Hir.Expression expr) {
      final var cached = typeSyntax.get(expr);
      if (cached != null) {
        valid &= cached;
        return;
      }
      final var outerValid = valid;
      valid = true;
      switch (expr) {
        case Hir.BuiltInTy _ -> {
        }
        case Hir.Identifier identifier -> {
          final var definition = definition(identifier.target());
          if (definition == null || identifier.target() instanceof Hir.Parameter || !resolving.add(definition)) {
            valid = false;
          } else {
            visitChild(definition);
            resolving.remove(definition);
          }
        }
        case Hir.Struct _, Hir.FunctionSignature _, Hir.Tuple _, Hir.Union _ -> expr.visit(this);
        case Hir.Array array when array.elements().length == 0 -> visitArrayElementType(array.elementType());
        case Hir.BinaryOperation binary when binary.kind() == Hir.BinaryOperationKind.BIT_OR ->
          HirVisitor.super.visitBinaryOperation(binary);
        case Hir.Dec _, Hir.Parameter _, Hir.TupleEntry _ -> expr.visit(this);
        default -> valid = false;
      }
      typeSyntax.put(expr, valid);
      valid &= outerValid;
    }
  }
}
