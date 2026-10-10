package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.hir.HirTypeDefinitions;
import org.inf.ty.util.MachineTarget;

import java.util.IdentityHashMap;
import java.util.Map;

/// Distinguishes type unions from bitwise OR after resolving operand names.
@UtilityClass
public class HirUnionSyntaxTransformerPass {

  public static Hir.Expression pass(final Hir.Expression root, final MachineTarget machineTarget) {
    final Map<Hir.Identifier, Hir.BuiltInTy> builtIns = new IdentityHashMap<>();
    final var resolver = new HirIdentifierResolverVisitorPass.Visitor(Hir.Identifier::target) {
      private boolean unionOperand;

      @Override
      public void visitUnion(final Hir.Union expr) {
        final var outer = unionOperand;
        try {
          unionOperand = true;
          super.visitUnion(expr);
        } finally {
          unionOperand = outer;
        }
      }

      @Override
      public void visitBinaryOperation(final Hir.BinaryOperation expr) {
        final var outer = unionOperand;
        try {
          unionOperand |= expr.kind() == Hir.BinaryOperationKind.BIT_OR;
          super.visitBinaryOperation(expr);
        } finally {
          unionOperand = outer;
        }
      }

      @Override
      public void visitArrayLength(final Hir.Expression expr) {
        final var outer = unionOperand;
        try {
          unionOperand = false;
          super.visitArrayLength(expr);
        } finally {
          unionOperand = outer;
        }
      }

      @Override
      public void visitIdentifier(final Hir.Identifier expr) {
        if (unionOperand && expr.target() == null && findInScope(expr.name()) == null) {
          final var builtIn = Hir.BuiltInTy.fromString(expr.name(), machineTarget);
          if (builtIn != null) {
            builtIns.put(expr, builtIn);
            return;
          }
        }
        super.visitIdentifier(expr);
      }
    };
    root.visit(resolver);
    if (!resolver.notFound.isEmpty()) {
      throw new IllegalArgumentException("Could not find '%s'".formatted(resolver.notFound));
    }
    final var named = root.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformIdentifier(final Hir.Identifier expr) {
        final var builtIn = builtIns.get(expr);
        return builtIn == null ? expr : builtIn;
      }
    });
    final var definitions = new HirTypeDefinitions(named);
    return named.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformBinaryOperation(final Hir.BinaryOperation expr) {
        HirTransformer.super.transformBinaryOperation(expr);
        if (expr.kind() != Hir.BinaryOperationKind.BIT_OR) {
          return expr;
        }
        final var leftType = definitions.isType(expr.lhs());
        final var rightType = definitions.isType(expr.rhs());
        if (leftType != rightType) {
          throw new IllegalArgumentException("'|' requires two type operands or two integer value operands");
        }
        return leftType ? Hir.Union.of(expr.lhs(), expr.rhs()) : expr;
      }
    });
  }
}
