package org.inf.hir.passes;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UtilityClass
public class HirLambdaLiftingTransformerPass {

  @Data
  private class FromTo {
    Hir.Function fromFunction;
    Hir.Assignment fromAssignment;
    Hir.Dec fromDec;
    Hir.Identifier fromIdentifier;

    Hir.Function toFunction;
    Hir.Assignment toAssignment;
    Hir.Dec toDec;
    Hir.Identifier toIdentifier;
  }

  public static Hir.Expression pass(Hir.Expression expr) {

    final var functionTransformer = new FunctionTransformer();
    var transformed = expr.transform(functionTransformer);

    final var callTransformer = new CallTransformer(functionTransformer.lifted);
    return transformed.transform(callTransformer);
  }

  @RequiredArgsConstructor
  private static class CallTransformer implements HirTransformer {

    private final List<FromTo> lifted;

    @Override
    public Hir.Expression transformCall(Hir.Call expr) {

      Hir.Expression target;
      if (expr.target() instanceof Hir.Identifier id) {
        target = id.target();
      } else {
        target = expr.target();
      }

      FromTo replacedWith = null;
      for (var entry : lifted) {
        if (target == entry.fromIdentifier() || target == entry.fromFunction() || target == entry.fromAssignment() || target == entry.fromDec()) {
          replacedWith = entry;
          break;
        }
      }

      if (replacedWith != null) {

        final var newArguments = new Hir.Argument[replacedWith.toFunction().signature().parameters().length];
        System.arraycopy(expr.arguments(), 0, newArguments, 0, expr.arguments().length);

        final var parameters = replacedWith.toFunction().signature().parameters();
        for (var i = expr.arguments().length; i < parameters.length; i++) {

          final var parameter = parameters[i];
          newArguments[i] = new Hir.Argument(
            parameter.lexeme(),
            new Hir.Identifier(parameter.lexeme(), parameter)
          );
        }

        return new Hir.Call(replacedWith.toIdentifier(), newArguments, expr.partial(), expr.ty());

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

          final var replacement = lifted.get(i);
          newExpressions[i] = replacement.toAssignment();
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
    public Hir.Expression transformAssignment(Hir.Assignment expr) {

      // TODO: Figure out way to do this automatically, without needing to replace stuff everywhere
      //        Feels like there is something behind identifiers pointing to declarations that might be a good idea

      final var originalRhs = expr.rhs();
      final var transformed = HirTransformer.super.transformAssignment(expr);

      if (transformed instanceof Hir.Assignment transformed_ass) {
        final var transformedRhs = transformed_ass.rhs();

        if (transformed_ass.lhs() instanceof Hir.Dec ass_lhs_dec && transformedRhs != originalRhs) {

          for (final var entry : lifted) {
            if (entry.toIdentifier() == transformedRhs) {
              entry.fromAssignment(expr);
              entry.fromDec(ass_lhs_dec);
              break;
            }
          }

//          if (ass_lhs_dec.valueType().ty() != transformedRhs.ty()) {
//            transformed_ass.lhs(new Hir.Dec(ass_lhs_dec.lexeme(), ass_lhs_dec.mutabilityKind(), new Hir.TyExpr(transformedRhs.ty())));
//          }
        }
      }

      return transformed;
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
        final var parameterTys = transformed.signature().ty().parameters();

        final var newParameters = new Hir.Parameter[parameters.length + needsClosure.size()];
        final var newParametersTy = new TyParam[newParameters.length];

        System.arraycopy(parameters, 0, newParameters, 0, parameters.length);
        System.arraycopy(parameterTys, 0, newParametersTy, 0, parameterTys.length);

        for (var i = 0; i < needsClosure.size(); i++) {

          final var id = needsClosure.get(i);
          final var ty = id.ty();
          final var typeExpr = new Hir.TyExpr(ty);

          final var newParam = new Hir.Parameter(id.lexeme(), typeExpr, false, ty);

          newParameters[parameters.length + i] = newParam;
          newParametersTy[parameters.length + i] = new TyParam(id.lexeme().name(), ty);
        }

        transformed.signature().parameters(newParameters);
        transformed.signature().ty(new TyFn(
          newParametersTy,
          transformed.signature().vararg(),
          transformed.signature().returnType().ty()
        ));

        final var randomName = UUID.randomUUID().toString();

        final var newIdentifier = new Hir.Identifier(
          new Hir.Lexeme(randomName, null),
          transformed
        );

        final var newDec = new Hir.Dec(
          new Hir.Lexeme(randomName, null),
          Hir.MutabilityKind.CONSTANT,
          new Hir.TyExpr(transformed.ty())
        );

        final var newAssignment = new Hir.Assignment(
          newDec,
          transformed
        );

        final var fromTo = new FromTo();
        fromTo.fromFunction(expr);
        fromTo.toFunction(transformed);
        fromTo.toAssignment(newAssignment);
        fromTo.toIdentifier(newIdentifier);
        fromTo.toDec(newDec);

        lifted.add(fromTo);

        // Replace with an identifier pointing to the hoisted function.
        return newIdentifier;
      } else {
        return expr;
      }
    }
  }
}
