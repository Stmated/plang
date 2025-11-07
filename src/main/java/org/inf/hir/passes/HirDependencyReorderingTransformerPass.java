package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.hir.HirVisitor;

@UtilityClass
public class HirDependencyReorderingTransformerPass {

  private record FromTo(Hir.Function from, Hir.Function to) {

  }

  // TODO: IMPLEMENT!
  // TODO: But even when this is implemented, the MIR -> LLVM stage will still need to first find all the functions and create the functions first.
  //        Because there might be cyclic dependencies between them and that should be all right.

  public static Hir.Expression pass(Hir.Expression expr) {

    final var dependencyVisitor = new DependencyVisitor();
    expr.visit(dependencyVisitor);

    final var reorderTransformer = new ReorderTransformer(dependencyVisitor);
    return expr.transform(reorderTransformer);
  }

  @RequiredArgsConstructor
  private static class ReorderTransformer implements HirTransformer {

    private final DependencyVisitor dependencyVisitor;

  }

  private static class DependencyVisitor implements HirVisitor {

  }
}
