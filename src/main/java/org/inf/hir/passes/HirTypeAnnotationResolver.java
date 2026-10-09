package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/// Resolves source type syntax without treating arbitrary value expressions as types.
final class HirTypeAnnotationResolver {

  private final Map<Hir.Dec, Hir.Expression> definitions = new IdentityHashMap<>();

  HirTypeAnnotationResolver(final Hir.Expression root) {
    root.visit(new HirVisitor() {
      @Override
      public void visitAssignment(final Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Dec declaration) {
          definitions.put(declaration, assignment.rhs());
        }
        HirVisitor.super.visitAssignment(assignment);
      }
    });
  }

  Ty resolve(final Hir.Expression expression) {
    new TypeSyntaxVisitor().visitType(expression);
    if (expression instanceof Hir.Identifier identifier) {
      return Tys.getBindingTy(identifier.target());
    }
    if (expression instanceof Hir.Array array) {
      return array.arrayTy();
    }
    return expression.ty();
  }

  private final class TypeSyntaxVisitor implements HirVisitor {

    private final Set<Hir.Expression> resolving = Collections.newSetFromMap(new IdentityHashMap<>());
    private final HirVisitor typePosition = new HirVisitor() {
      @Override
      public void visitChild(final Hir.Expression expression) {
        visitType(expression);
      }
    };

    @Override
    public void visitChild(final Hir.Expression expression) {
      switch (expression) {
        case Hir.TupleEntry _, Hir.Dec _, Hir.Parameter _ -> expression.visit(this);
        default -> visitType(expression);
      }
    }

    private void visitType(final Hir.Expression expression) {
      switch (expression) {
        case Hir.BuiltInTy _ -> {
        }
        case Hir.Identifier identifier -> visitDefinition(identifier.target());
        case Hir.Struct _, Hir.FunctionSignature _, Hir.Tuple _ -> expression.visit(this);
        case Hir.Array array when array.elements().length == 0 -> array.elementType().visit(typePosition);
        default -> throw invalidAnnotation();
      }
    }

    private void visitDefinition(final Hir.Expression target) {
      if (target == null || !resolving.add(target)) {
        throw invalidAnnotation();
      }
      try {
        if (target instanceof Hir.Dec declaration) {
          final var definition = definitions.get(declaration);
          if (definition == null) {
            throw invalidAnnotation();
          }
          visitType(definition);
        } else {
          switch (target) {
            case Hir.Struct _, Hir.FunctionSignature _, Hir.Array _, Hir.Identifier _ -> visitType(target);
            default -> throw invalidAnnotation();
          }
        }
      } finally {
        resolving.remove(target);
      }
    }

    @Override
    public void visitTupleEntry(final Hir.TupleEntry entry) {
      visitType(entry.value());
    }

    @Override
    public void visitParameter(final Hir.Parameter parameter) {
      parameter.typeAnnotation().visit(typePosition);
    }

    @Override
    public void visitDec(final Hir.Dec declaration) {
      declaration.typeAnnotation().visit(typePosition);
    }

    @Override
    public void visitFunctionSignatureReturnType(final Hir.DynamicTy annotation) {
      annotation.visit(typePosition);
    }
  }

  private static IllegalArgumentException invalidAnnotation() {
    return new IllegalArgumentException("Type annotations require types, not value expressions");
  }
}
