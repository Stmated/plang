package com.github.stmated.plang.hir.passes;

import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.HirVisitor;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyValueString;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HirIdentifierResolverVisitorPass {

  public static void pass(Hir.Expression expr, Function<Hir.Identifier, Hir.Expression> identifierTargetResolver) {

    final var visitor = new Visitor(identifierTargetResolver);
    expr.visit(visitor);

    if (!visitor.notFound.isEmpty()) {
      throw new IllegalArgumentException(STR."Could not find '\{visitor.notFound}'");
    }
  }

  @RequiredArgsConstructor
  public static class Visitor implements HirVisitor {

    private record Scope(Map<String, Hir.Expression> map) { }

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

//        Ty ty;
//        if (found instanceof Hir.Parameter param) {
//          ty = param.valueType().ty();
//        } else if (found instanceof Hir.Assignment ass) {
//          ty = ass.rhs().ty();
//        } else if (found instanceof Hir.Dec dec) {
//          ty = dec.valueType().ty();
//        } else {
//          ty = found.ty();
//        }

//        if (ty == null) {
//
//          // If the ty is unknown, then it will need to be inferred at a later stage.
//          // It is a good idea to try to resolve the ty as early as possible to propagate it throughout.
//          ty = Ty.INFER;
//        }

//        expr.ty(ty);
      }
    }
  }
}
