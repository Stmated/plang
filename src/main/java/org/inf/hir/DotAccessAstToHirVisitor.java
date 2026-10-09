package org.inf.hir;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.inf.ast.Ast;
import org.inf.ast.AstVisitor;
import org.inf.exceptions.UnexpectedExpressionException;

import java.util.function.Function;

@RequiredArgsConstructor
class DotAccessAstToHirVisitor implements AstVisitor<Void> {

  private final Function<Ast.Expression, Hir.Expression> raiser;

  @Getter
  private Hir.Expression target;

  @Override
  public Void visit(final Ast.Expression expr) {
    if (expr instanceof Ast.DotAccess) {
      return expr.visit(this);
    }
    final var raised = this.raiser.apply(expr);
    target = target == null ? raised : new DotAccessAstToHirMemberTargetTransformer(target).transformTarget(raised);
    return null;
  }

  private record DotAccessAstToHirMemberTargetTransformer(Hir.Expression target) implements HirTransformer {

    public Hir.Expression transformTarget(final Hir.Expression expression) {
      return switch (expression) {
        case Hir.Lexeme _, Hir.ArrayAccess _, Hir.Call _ -> expression.transform(this);
        default -> throw new UnexpectedExpressionException(expression);
      };
    }

    @Override
    public Hir.Expression transformArrayAccess(final Hir.ArrayAccess expression) {
      // Only the member target attaches to the receiver; indices keep their own scope.
      expression.target(transformTarget(expression.target()));
      return expression;
    }

    @Override
    public Hir.Expression transformCall(final Hir.Call expression) {
      expression.target(transformTarget(expression.target()));
      return expression;
    }

    @Override
    public Hir.Expression transformLexeme(final Hir.Lexeme expression) {
      return new Hir.DotAccess(target, expression.name());
    }
  }
}
