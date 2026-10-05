package org.inf.hir.passes;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.hir.HirVisitor;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@UtilityClass
public class HirLambdaLiftingTransformerPass {

  private record Capture(Hir.Expression declaration, Hir.Parameter parameter) {
  }

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
    List<Capture> captures;
  }

  public static Hir.Expression pass(Hir.Expression expr) {

    final Set<Hir.Expression> staticFunctions = Collections.newSetFromMap(new IdentityHashMap<>());
    expr.visit(new HirVisitor() {
      @Override
      public void visitAssignment(Hir.Assignment assignment) {
        if (assignment.lhs() instanceof Hir.Dec declaration
          && declaration.mutabilityKind() != Hir.MutabilityKind.MUTABLE
          && (assignment.rhs() instanceof Hir.Function || assignment.rhs() instanceof Hir.FunctionSignature)) {
          staticFunctions.add(declaration);
        }
        HirVisitor.super.visitAssignment(assignment);
      }
    });
    final var functionTransformer = new FunctionTransformer(staticFunctions);
    var transformed = expr.transform(functionTransformer);

    return transformed.transform(functionTransformer.callTransformer);
  }

  @RequiredArgsConstructor
  private static class CallTransformer implements HirTransformer {

    private final List<FromTo> lifted;
    private final Set<Hir.Call> rewritten = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<Map<Hir.Expression, Hir.Parameter>> captureScopes = new ArrayDeque<>();

    @Override
    public Hir.Expression transformFunction(Hir.Function expr) {
      final var bindings = new IdentityHashMap<Hir.Expression, Hir.Parameter>();
      for (final var entry : lifted) {
        if (entry.toFunction() == expr) {
          for (final var capture : entry.captures()) {
            bindings.put(capture.declaration(), capture.parameter());
          }
          break;
        }
      }
      captureScopes.push(bindings);
      try {
        return HirTransformer.super.transformFunction(expr);
      } finally {
        captureScopes.pop();
      }
    }

    @Override
    public Hir.Expression transformCall(Hir.Call expr) {

      expr = expect(HirTransformer.super.transformCall(expr), Hir.Call.class);
      if (rewritten.contains(expr)) {
        return expr;
      }

      Hir.Expression target;
      if (expr.target() instanceof Hir.Identifier id) {
        target = id.target();
      } else {
        target = expr.target();
      }

      FromTo replacedWith = null;
      for (var entry : lifted) {
        if (target == entry.fromIdentifier() || target == entry.fromFunction() || target == entry.fromAssignment()
          || target == entry.fromDec() || target == entry.toFunction() || target == entry.toDec()) {
          replacedWith = entry;
          break;
        }
      }

      if (replacedWith != null) {

        final var newArguments = new Hir.Argument[expr.arguments().length + replacedWith.captures().size()];
        System.arraycopy(expr.arguments(), 0, newArguments, 0, expr.arguments().length);

        for (var i = 0; i < replacedWith.captures().size(); i++) {

          final var capture = replacedWith.captures().get(i);
          final var parameter = capture.parameter();
          Hir.Expression declaration = capture.declaration();
          for (final var bindings : captureScopes) {
            final var local = bindings.get(declaration);
            if (local != null) {
              declaration = local;
              break;
            }
          }
          newArguments[expr.arguments().length + i] = new Hir.Argument(
            parameter.lexeme(),
            new Hir.Identifier(parameter.lexeme(), declaration)
          );
        }

        final var replacement = new Hir.Call(
          replacedWith.toIdentifier(), newArguments, expr.partial(), expr.ty(), null
        );
        rewritten.add(replacement);
        return replacement;

      } else {
        return expr;
      }
    }
  }

  @RequiredArgsConstructor
  private static class FunctionTransformer implements HirTransformer {

    private final Set<Hir.Expression> staticFunctions;
    private final List<FromTo> lifted = new ArrayList<>();
    private final CallTransformer callTransformer = new CallTransformer(lifted);

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

          staticFunctions.remove(ass_lhs_dec);
          for (final var entry : lifted) {
            if (entry.toIdentifier() == transformedRhs) {
              entry.fromAssignment(expr);
              entry.fromDec(ass_lhs_dec);
              ass_lhs_dec.valueType(new Hir.TyExpr(transformedRhs.ty()));
              break;
            }
          }
        }
      }

      return transformed;
    }

    @Override
    public Hir.Expression transformFunction(Hir.Function expr) {

      expr = expect(HirTransformer.super.transformFunction(expr), Hir.Function.class);
      // Expose nested callees' captures before finding this function's own free variables.
      expr.body(expr.body().transform(callTransformer));

      final var freeIdentifiers = freeIdentifiers(expr);

      if (!freeIdentifiers.isEmpty()) {

        final var needsClosure = new ArrayList<Hir.Identifier>();
        for (final var item : freeIdentifiers) {

          if (staticFunctions.contains(item.target()) || item.target() instanceof Hir.Function
            || item.target() instanceof Hir.FunctionSignature) {
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
        final var captures = new ArrayList<Capture>();
        final var captureBindings = new IdentityHashMap<Hir.Expression, Hir.Parameter>();

        for (var i = 0; i < needsClosure.size(); i++) {

          final var id = needsClosure.get(i);
          final var ty = id.ty();
          final var typeExpr = new Hir.TyExpr(ty);

          final var newParam = new Hir.Parameter(id.lexeme(), typeExpr, false, ty);

          newParameters[parameters.length + i] = newParam;
          newParametersTy[parameters.length + i] = new TyParam(id.lexeme().name(), ty);
          captures.add(new Capture(id.target(), newParam));
          captureBindings.put(id.target(), newParam);
        }

        transformed.body(transformed.body().transform(new HirTransformer() {
          @Override
          public Hir.Expression transformIdentifier(Hir.Identifier identifier) {
            final var parameter = captureBindings.get(identifier.target());
            if (parameter != null) {
              identifier.target(parameter);
            }
            return identifier;
          }

          @Override
          public Hir.Expression transformFunction(Hir.Function function) {
            return function;
          }
        }));

        transformed.signature().parameters(newParameters);
        transformed.signature().ty(new TyFn(
          newParametersTy,
          transformed.signature().vararg(),
          transformed.signature().returnType().ty()
        ));

        final var randomName = UUID.randomUUID().toString();

        final var newIdentifier = new Hir.Identifier(
          new Hir.Lexeme(randomName),
          transformed
        );

        final var newDec = new Hir.Dec(
          new Hir.Lexeme(randomName),
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
        fromTo.captures(captures);

        lifted.add(fromTo);

        // Replace with an identifier pointing to the hoisted function.
        return newIdentifier;
      } else {
        return expr;
      }
    }

    private List<Hir.Identifier> freeIdentifiers(Hir.Function function) {
      final Set<Hir.Expression> declarations = Collections.newSetFromMap(new IdentityHashMap<>());
      final var identifiers = new ArrayList<Hir.Identifier>();
      final var visitor = new HirVisitor() {
        @Override
        public void visitFunction(Hir.Function nested) {
        }

        @Override
        public void visitParameter(Hir.Parameter parameter) {
          declarations.add(parameter);
        }

        @Override
        public void visitFunctionSignatureReturnType(Hir.Expression expression) {
        }

        @Override
        public void visitDec(Hir.Dec declaration) {
          declarations.add(declaration);
        }

        @Override
        public void visitIdentifier(Hir.Identifier identifier) {
          identifiers.add(identifier);
        }

        @Override
        public void visitNewByBlock(Hir.NewByBlock creation) {
          for (final var field : creation.fields()) {
            field.rhs().visit(this);
          }
        }

        @Override
        public void visitNewByCtor(Hir.NewByCtor creation) {
          if (creation.arguments() != null) {
            creation.arguments().visit(this);
          }
        }

        @Override
        public void visitPath(Hir.Path path) {
          if (path.elements().length > 0) {
            path.elements()[0].visit(this);
          }
          for (var i = 1; i < path.elements().length; i++) {
            if (path.elements()[i] instanceof Hir.Call call) {
              for (final var argument : call.arguments()) {
                argument.visit(this);
              }
            }
          }
        }
      };
      function.signature().visit(visitor);
      function.body().visit(visitor);
      return identifiers.stream().filter(identifier -> !declarations.contains(identifier.target())).toList();
    }
  }
}
