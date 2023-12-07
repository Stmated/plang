package com.github.stmated.plang.hir.passes;

import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.HirTransformer;
import com.github.stmated.plang.ty.TyFn;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HirLambdaLiftingTransformerPass {

  private record FromTo(Hir.Function from, Hir.Function to) {

  }

  public static Hir.Expression pass(Hir.Expression expr) {

    final var functionTransformer = new FunctionTransformer();
    var transformed = expr.transform(functionTransformer);

    final var callTransformer = new CallTransformer(functionTransformer.lifted);
    return transformed.transform(callTransformer);
  }

  @RequiredArgsConstructor
  private static class CallTransformer implements HirTransformer {

    private final Map<Hir.Expression, Hir.Function> lifted = new LinkedHashMap<>();

    public CallTransformer(List<FromTo> listed) {
      for (final var fromTo : listed) {
        lifted.put(fromTo.from(), fromTo.to());
      }
    }

    // TODO: Might need to replace all identifiers that point to the original fn to the lifted fn

    @Override
    public Hir.Expression transformIdentifier(Hir.Identifier expr) {

      final var replacement = lifted.get(expr.target());
      if (replacement != null) {
        var i = 0;
      }

      return HirTransformer.super.transformIdentifier(expr);
    }

    @Override
    public Hir.Expression transformAssignment(Hir.Assignment expr) {

      final var replacement = lifted.get(expr.rhs());
      if (replacement != null) {
        return new Hir.Assignment(expr.lhs(), new Hir.Reference(replacement, replacement.ty()));

//        return replacement;
      }

      return HirTransformer.super.transformAssignment(expr);
    }

    @Override
    public Hir.Expression transformCall(Hir.Call expr) {

      final Hir.Function replacedWith;
      if (expr.target() instanceof Hir.Identifier id) {
        replacedWith = lifted.get(id.target());
      } else {
        replacedWith = lifted.get(expr.target());
      }

      if (replacedWith != null) {

        final var newArguments = new Hir.Argument[replacedWith.signature().parameters().length];
        System.arraycopy(expr.arguments(), 0, newArguments, 0, expr.arguments().length);

        final var parameters = replacedWith.signature().parameters();
        for (var i = expr.arguments().length; i < parameters.length; i++) {

          final var parameter = parameters[i];
          newArguments[i] = new Hir.Argument(
            parameter.lexeme(),
            new Hir.Identifier(parameter.lexeme(), parameter, parameter.ty())
          );
        }

        return new Hir.Call(replacedWith, newArguments, expr.partial(), expr.ty());

      } else {
        return HirTransformer.super.transformCall(expr);
      }
    }
  }

  private static class FunctionTransformer implements HirTransformer {

    private final List<FromTo> lifted = new ArrayList<>();

    @Override
    public Hir.Expression transformProgram(Hir.Program expr) {

      final var program = expect(HirTransformer.super.transformProgram(expr), Hir.Program.class);
      if (!lifted.isEmpty()) {

        final var existingExpressions = expand(program.expressions());
        final var newExpressions = new Hir.Expression[lifted.size() + existingExpressions.length];
        for (var i = 0; i < lifted.size(); i++) {
          newExpressions[i] = lifted.get(i).to();
        }

        System.arraycopy(existingExpressions, 0, newExpressions, lifted.size(), existingExpressions.length);

        program.expressions(new Hir.Expressions(newExpressions, null));
      }

      return program;
    }

    private Hir.Expression[] expand(Hir.Expression expr) {
      if (expr instanceof Hir.Expressions exprs) {
        return exprs.children();
      } else {
        return new Hir.Expression[]{expr};
      }
    }

    @Override
    public Hir.Expression transformFunction(Hir.Function expr) {

      expr = expect(HirTransformer.super.transformFunction(expr), Hir.Function.class);

      HirIdentifierResolverVisitorPass.Visitor identifierResolverPass = new HirIdentifierResolverVisitorPass.Visitor(_ -> null);
      expr.visit(identifierResolverPass);

      if (!identifierResolverPass.notFound.isEmpty()) {

        final var needsClosure = new ArrayList<Hir.Identifier>();
        for (final var item : identifierResolverPass.notFound) {

          if (item.ty() instanceof TyFn) {

            // We do not need to lift references to fn, since it will be global.
            continue;
          }

          var found = false;
          for (final var existing : needsClosure) {
            if (item.target() == existing.target()) {
              found = true;
              break;
            }
          }

          if (!found) {
            needsClosure.add(item);
          }
        }

        if (needsClosure.isEmpty()) {
          return expr;
        }

        final var transformed = new Hir.Function(
          new Hir.FunctionSignature(
            expr.signature().parameters(),
            expr.signature().vararg(),
            expr.signature().returnType(),
            expr.signature().ty()
          ),
          expr.body()
        );

        // If the identifier is not found inside the function itself, then it exists in an owning function.
        // We will add these as parameters to the function.

        final var parameters = transformed.signature().parameters();
        final var newParameters = new Hir.Parameter[parameters.length + needsClosure.size()];

        System.arraycopy(parameters, 0, newParameters, 0, parameters.length);

        for (var i = 0; i < needsClosure.size(); i++) {

          final var id = needsClosure.get(i);
          final var ty = id.ty();
          final var typeExpr = new Hir.TyExpr(ty);

          final var newParam = new Hir.Parameter(id.lexeme(), typeExpr, false, ty);

          newParameters[parameters.length + i] = newParam;
        }

        transformed.signature().parameters(newParameters);

        lifted.add(new FromTo(expr, transformed));

        // Replace with a reference to the hoisted function.
        return expr;
      } else {
        return expr;
      }
    }
  }
}
