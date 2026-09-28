package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;

import java.util.*;
import java.util.function.Function;

@UtilityClass
public class HirIdentifierResolverVisitorPass {

  public static void pass(Hir.Expression expr, Function<Hir.Identifier, Hir.Expression> identifierTargetResolver) {

    final var visitor = new Visitor(identifierTargetResolver);
    expr.visit(visitor);

    if (!visitor.notFound.isEmpty()) {
      throw new IllegalArgumentException("Could not find '" + visitor.notFound + "'");
    }
  }

  @RequiredArgsConstructor
  public static class Visitor implements HirVisitor {

    private record Scope(Map<String, Hir.Expression> map) {
    }

    private final Function<Hir.Identifier, Hir.Expression> identifierTargetResolver;
    private final Deque<Scope> scopes = new ArrayDeque<>();
    public final List<Hir.Identifier> notFound = new ArrayList<>();

    {
      this.scopes.push(new Scope(new HashMap<>()));
    }

    private Hir.Expression find(Hir.Identifier id) {

      final var name = id.lexeme().name();

      for (final var scope : scopes) {
        final var expr = scope.map().get(name);
        if (expr != null) {
          return expr;
        }
      }

      notFound.add(id);
      return null;
    }

    @Override
    public void visitFunction(Hir.Function expr) {
      try {

        final var scope = new Scope(new HashMap<>());
        scopes.push(scope);
        HirVisitor.super.visitFunction(expr);
      } finally {
        scopes.pop();
      }
    }

    @Override
    public void visitBlock(Hir.Block expr) {

      try {
        scopes.push(new Scope(new HashMap<>()));
        HirVisitor.super.visitBlock(expr);
      } finally {
        scopes.pop();
      }
    }

    @Override
    public void visitParameter(Hir.Parameter expr) {

      scopes.peek().map().put(expr.lexeme().name(), expr);
      HirVisitor.super.visitParameter(expr);
    }

    @Override
    public void visitAssignment(Hir.Assignment expr) {

      if (expr.lhs() instanceof Hir.Dec dec) {

        // The assignment is to the "declaration" which in a way is the Place/address.
        scopes.peek().map().put(dec.lexeme().name(), dec);
      }

      HirVisitor.super.visitAssignment(expr);
    }

    /**
     * TODO: Right now we do not care about resolving the allocator, since there is no support yet :)
     */
    @Override
    public void visitAllocator(Hir.Identifier expr) {
      expr.target(new Hir.Literal("", Ty.STRING));
    }

    @Override
    public void visitIdentifier(Hir.Identifier expr) {
      HirVisitor.super.visitIdentifier(expr);
      final var existing = identifierTargetResolver.apply(expr);
      if (existing != null) {
        return;
      }

      final var found = find(expr);
      if (found != null) {
        expr.target(found);
      }
    }
  }
}
