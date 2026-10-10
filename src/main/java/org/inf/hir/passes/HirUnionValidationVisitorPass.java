package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirSpreadShape;
import org.inf.hir.HirTypeDefinitions;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyParam;
import org.inf.ty.TyUnion;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.Tys;
import org.inf.ty.util.UnionTypes;
import org.inf.util.ArrayUtils;

/// Checks union value boundaries and prevents type-only definitions from becoming runtime values.
@UtilityClass
public class HirUnionValidationVisitorPass {

  public static void pass(final Hir.Expression root) {
    root.visit(new Visitor(root));
  }

  private static void check(final Ty actual, final Ty expected, final String context) {
    if ((actual instanceof TyUnion || expected instanceof TyUnion) && !UnionTypes.compatible(actual, expected)) {
      throw new InvalidTypeConversionException("Value is not compatible with union in %s".formatted(context), actual, expected);
    }
  }

  private static final class Visitor implements HirVisitor {

    private final HirTypeDefinitions definitions;
    private boolean typePosition;
    private Ty returnType;

    private Visitor(final Hir.Expression root) {
      definitions = new HirTypeDefinitions(root);
    }

    private void visitType(final Hir.Expression expr) {
      final var outer = typePosition;
      try {
        typePosition = true;
        visitChild(expr);
      } finally {
        typePosition = outer;
      }
    }

    private void visitAnnotation(final Hir.DynamicTy annotation) {
      if (annotation.expression() != null) {
        visitType(annotation.expression());
      }
    }

    @Override
    public void visitDecType(final Hir.DynamicTy annotation) {
      visitAnnotation(annotation);
    }

    @Override
    public void visitParameterType(final Hir.DynamicTy annotation) {
      visitAnnotation(annotation);
    }

    @Override
    public void visitFunctionSignatureReturnType(final Hir.DynamicTy annotation) {
      visitAnnotation(annotation);
    }

    @Override
    public void visitArrayElementType(final Hir.DynamicTy annotation) {
      visitAnnotation(annotation);
    }

    @Override
    public void visitArrayLength(final Hir.Expression expr) {
      final var outer = typePosition;
      try {
        typePosition = false;
        visitChild(expr);
      } finally {
        typePosition = outer;
      }
    }

    @Override
    public void visitUnion(final Hir.Union expr) {
      if (!typePosition) {
        throw new IllegalArgumentException("Type unions cannot be used as runtime values");
      }
      if (Tys.containsInferred(expr.unionTy())) {
        throw new IllegalArgumentException("Union members require resolved types");
      }
      HirVisitor.super.visitUnion(expr);
    }

    @Override
    public void visitIdentifier(final Hir.Identifier expr) {
      if (!typePosition && definitions.isUnionDefinition(expr)) {
        throw new IllegalArgumentException("Type union alias '%s' cannot be used as a runtime value".formatted(expr.name()));
      }
    }

    @Override
    public void visitBinaryOperation(final Hir.BinaryOperation expr) {
      HirVisitor.super.visitBinaryOperation(expr);
      if (expr.kind() == Hir.BinaryOperationKind.BIT_OR
        && (expr.lhs().ty() != Ty.DEADEND && !(expr.lhs().ty() instanceof TyValueNumberInteger)
          || expr.rhs().ty() != Ty.DEADEND && !(expr.rhs().ty() instanceof TyValueNumberInteger))) {
        throw new IllegalArgumentException("Bitwise '|' requires integer value operands");
      }
    }

    @Override
    public void visitAssignment(final Hir.Assignment expr) {
      if (expr.lhs() instanceof Hir.Dec declaration && definitions.isUnionDefinition(expr.rhs())) {
        if (!Tys.isInferred(declaration.typeAnnotation().ty())) {
          throw new IllegalArgumentException("Type unions cannot initialize runtime bindings");
        }
        visitChild(declaration);
        visitType(expr.rhs());
      } else {
        HirVisitor.super.visitAssignment(expr);
        check(expr.rhs().ty(), Tys.getBindingTy(expr.lhs()), "assignment");
      }
    }

    @Override
    public void visitNewByBlockField(final Hir.NewByBlock construction, final Hir.Assignment expr) {
      HirVisitor.super.visitAssignment(expr);
      final var field = Tys.getInitializerField(construction, expr);
      if (field != null) {
        check(expr.rhs().ty(), field.ty(), "field %s".formatted(expr.lhs()));
      }
    }

    @Override
    public void visitArray(final Hir.Array expr) {
      HirVisitor.super.visitArray(expr);
      for (final var element : expr.elements()) {
        check(element.ty(), expr.elementType().ty(), "array element");
      }
    }

    @Override
    public void visitFunction(final Hir.Function expr) {
      final var outerReturn = returnType;
      try {
        returnType = expr.signature().ty().returnTy();
        HirVisitor.super.visitFunction(expr);
        check(HirBodyResultTyping.resolve(expr.body()), returnType, "function result");
      } finally {
        returnType = outerReturn;
      }
    }

    @Override
    public void visitReturn(final Hir.Return expr) {
      HirVisitor.super.visitReturn(expr);
      if (returnType != null) {
        check(expr.expression().ty(), returnType, "return");
      }
    }

    @Override
    public void visitCall(final Hir.Call expr) {
      HirVisitor.super.visitCall(expr);
      final var function = Tys.getCallableSignature(expr.target());
      if (function == null) {
        return;
      }
      final var arguments = expr.arguments();
      if (ArrayUtils.none(function.parameters(), parameter -> parameter.ty() instanceof TyUnion)
        && ArrayUtils.none(arguments, argument -> argument.ty() instanceof TyUnion)) {
        return;
      }
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), HirCallArguments.count(arguments)
      );
      for (final var argument : arguments) {
        final var indices = HirCallArguments.bind(argument, binding);
        final var fields = argument.value() instanceof Hir.Spread spread ? HirSpreadShape.fields(spread) : null;
        for (var i = 0; i < indices.length; i++) {
          final var index = indices[i];
          if (index >= 0 && index < parameters.length) {
            check(fields == null ? argument.ty() : fields[i].ty(), parameters[index].ty(), "argument %s".formatted(index));
          }
        }
      }
      if (expr.ty() != Ty.DEADEND || ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread)) {
        binding.requireComplete();
      }
    }
  }
}
