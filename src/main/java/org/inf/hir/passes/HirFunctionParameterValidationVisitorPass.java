package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.ty.TyFn;
import org.inf.ty.util.TypeComparison;

/// Checks resolved lambda parameters after contextual inference, before dependent body typing.
@UtilityClass
public class HirFunctionParameterValidationVisitorPass {

  public static void pass(final Hir.Expression expression) {
    HirFunctionContextVisitor.visit(expression, HirFunctionParameterValidationVisitorPass::checkParameters);
  }

  private static void checkParameters(final Hir.FunctionSignature signature, final TyFn expected) {
    final var parameters = signature.parameters();
    if (parameters.length != expected.parameters().length || signature.vararg() != expected.vararg()) {
      throw new IllegalArgumentException("Lambda parameter count or variadic shape does not match expected function type");
    }
    for (var i = 0; i < parameters.length; i++) {
      final var actual = parameters[i].valueType().ty();
      final var destination = expected.parameters()[i].ty();
      if (!TypeComparison.sameValueType(actual, destination)) {
        throw new InvalidTypeConversionException("Lambda parameter type does not match expected function type", actual, destination);
      }
    }
  }

}
