package org.inf.hir;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * TODO: In general, all visits should be to a UNIQUE function, so Dec should have a DecType class that THEN have a "type"
 *        This way we can override the visit/transform of ONLY the DecType
 */
public interface HirTransformer {

  default Hir.Expression join(final Hir.Expression[] values) {
    return new Hir.Expressions(values);
  }

  default Hir.Expression none() {
    return null;
  }

  default Hir.Expression convert(final Hir.Expression expr) {
    return expr;
  }

  default <T extends Hir.Expression> T[] batch(final T[] expressions, final Class<T> clazz) {

    final var list = new ArrayList<T>(expressions.length);
    for (final var child : expressions) {
      final var transformed = expect(child.transform(this), clazz);
      if (transformed != null) {
        list.add(transformed);
      }
    }

    final var array = (T[]) Array.newInstance(clazz, 0);
    return list.toArray(array);
  }

  default <R extends Hir.Expression> R expect(final Hir.Expression expr, final Class<R> clazz) {
    if (clazz.isAssignableFrom(expr.getClass())) {
      return (R) expr;
    } else {
      throw new IllegalArgumentException("Expected '%s' to be a '%s'".formatted(expr, clazz));
    }
  }

  default Hir.Expression transformFunction(final Hir.Function expr) {

    expr.signature(expect(expr.signature().transform(this), Hir.FunctionSignature.class));
    expr.body(expect(expr.body().transform(this), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformExpressions(final Hir.Expressions expr) {
    expr.children(batch(expr.children(), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformArray(final Hir.Array expr) {
    expr.elementType(transformArrayElementType(expr.elementType()));
    expr.length(transformArrayLength(expr.length()));
    expr.elements(transformArrayElements(expr.elements()));

    return expr;
  }

  default Hir.Expression transformUnion(final Hir.Union expr) {
    expr.elements(batch(expr.elements(), Hir.Expression.class));
    return expr;
  }

  default Hir.DynamicTy transformArrayElementType(final Hir.DynamicTy expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformArrayLength(final Hir.Expression expr) {
    if (expr == null) {
      return null;
    }

    return expr.transform(this);
  }

  default Hir.Expression[] transformArrayElements(final Hir.Expression[] exprs) {
    return batch(exprs, Hir.Expression.class);
  }

  default Hir.Expression transformArrayAccess(final Hir.ArrayAccess expr) {
    expr.target(expr.target().transform(this));
    expr.accessor(expr.accessor().transform(this));
    return expr;
  }

  default Hir.Expression transformAssignment(final Hir.Assignment expr) {
    final var lhs = transformAssignmentLhs(expr.lhs());
    final var rhs = transformAssignmentRhs(expr.rhs());
    return new Hir.Assignment(lhs, rhs, expr.ty());
  }

  default Hir.Expression transformAssignmentLhs(final Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformAssignmentRhs(final Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformBinaryOperation(final Hir.BinaryOperation expr) {
    expr.lhs(expr.lhs().transform(this));
    expr.rhs(expr.rhs().transform(this));
    return expr;
  }

  default Hir.Expression transformCompoundAssignment(final Hir.CompoundAssignment expr) {
    expr.target(expr.target().transform(this));
    expr.rhs(expr.rhs().transform(this));
    return expr;
  }

  default Hir.Expression transformConvert(final Hir.Convert expr) {
    expr.expression(expr.expression().transform(this));
    return expr;
  }

  default Hir.Expression transformBlock(final Hir.Block expr) {
    expr.children(expr.children().transform(this));
    return expr;
  }

  default Hir.Expression transformConditional(final Hir.Conditional expr) {
    expr.predicate(expr.predicate().transform(this));
    expr.pass(expr.pass().transform(this));
    if (expr.fail() != null) {
      expr.fail(expr.fail().transform(this));
    }
    return expr;
  }

  default Hir.Expression transformFunctionSignature(final Hir.FunctionSignature expr) {
    expr.parameters(transformFunctionSignatureParameters(expr.parameters()));
    expr.returnTypeAnnotation(transformFunctionSignatureReturnType(expr.returnTypeAnnotation()));

    return expr;
  }

  default Hir.Parameter[] transformFunctionSignatureParameters(final Hir.Parameter[] expr) {
    return batch(expr, Hir.Parameter.class);
  }

  default Hir.DynamicTy transformFunctionSignatureReturnType(final Hir.DynamicTy expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformIdentifier(final Hir.Identifier expr) {
    return expr;
  }

  default Hir.Expression transformBuiltInTy(final Hir.BuiltInTy expr) {
    return expr;
  }

  default Hir.Expression transformLabeling(final Hir.Labeling expr) {
    expr.lhs(expr.lhs().transform(this));
    expr.rhs(expr.rhs().transform(this));
    return expr;
  }

  default Hir.Expression transformLiteral(final Hir.Literal expr) {
    return expr;
  }

  default Hir.Expression transformLoop(final Hir.Loop expr) {
    expr.body(expr.body().transform(this));
    return expr;
  }

  default Hir.Expression transformLoopBreak(final Hir.LoopBreak expr) {
    if (expr.value() != null) {
      expr.value(expr.value().transform(this));
    }
    return expr;
  }

  default Hir.Expression transformLoopContinue(final Hir.LoopContinue expr) {
    return expr;
  }

  default Hir.Expression transformNewByBlock(final Hir.NewByBlock expr) {
    expr.target(expr.target().transform(this));
    expr.allocator(transformAllocator(expr.allocator()));
    expr.fields(transformNewByBlockFields(expr.fields()));

    return expr;
  }

  default Hir.Identifier transformAllocator(final Hir.Identifier expr) {
    if (expr == null) {
      return null;
    }

    return expect(expr.transform(this), Hir.Identifier.class);
  }

  default Hir.Assignment[] transformNewByBlockFields(final Hir.Assignment[] fields) {
    return batch(fields, Hir.Assignment.class);
  }

  default Hir.Expression transformNewByCtor(final Hir.NewByCtor expr) {
    expr.target(expr.target().transform(this));
    expr.allocator(transformAllocator(expr.allocator()));

    if (expr.arguments() != null) {
      expr.arguments(expr.arguments().transform(this));
    }

    return expr;
  }

  default Hir.Expression transformNot(final Hir.Not expr) {
    expr.expression(expr.expression().transform(this));
    return expr;
  }

  default Hir.Expression transformParameter(final Hir.Parameter expr) {
    expr.lexeme(transformParameterName(expr.lexeme()));
    expr.typeAnnotation(transformParameterType(expr.typeAnnotation()));
    return expr;
  }

  default Hir.Lexeme transformParameterName(final Hir.Lexeme expr) {
    return expect(expr.transform(this), Hir.Lexeme.class);
  }

  default Hir.DynamicTy transformParameterType(final Hir.DynamicTy expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformDotAccess(final Hir.DotAccess expr) {
    expr.target(expr.target().transform(this));
    return expr;
  }

  default Hir.Expression transformRange(final Hir.Range expr) {
    expr.lower(expr.lower().transform(this));
    expr.higher(expr.higher().transform(this));
    return expr;
  }

  default Hir.Expression transformProgram(final Hir.Program expr) {
    expr.expressions(expr.expressions().transform(this));
    return expr;
  }

  default Hir.Expression transformReturn(final Hir.Return expr) {
    expr.expression(expr.expression().transform(this));
    return expr;
  }

  default Hir.Expression transformDeadEnd(final Hir.DeadEnd expr) {
    expr.expression(expr.expression().transform(this));
    return expr;
  }

  default Hir.Expression transformStruct(final Hir.Struct expr) {
    expr.declarations(batch(expr.declarations(), Hir.Dec.class));
    return expr;
  }

  default Hir.Expression transformTrait(final Hir.Trait expr) {
    expr.children(batch(expr.children(), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformTuple(final Hir.Tuple expr) {
    expr.children(batch(expr.children(), Hir.TupleEntry.class));
    return expr;
  }

  default Hir.Expression transformTupleEntry(final Hir.TupleEntry expr) {
    expr.value(expr.value().transform(this));
    return expr;
  }

  default Hir.Expression transformDec(final Hir.Dec expr) {
    expr.lexeme(transformDecName(expr.lexeme));
    expr.typeAnnotation(transformDecType(expr.typeAnnotation()));
    return expr;
  }

  default Hir.Lexeme transformDecName(final Hir.Lexeme expr) {
    return expect(expr.transform(this), Hir.Lexeme.class);
  }

  default Hir.DynamicTy transformDecType(final Hir.DynamicTy expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformCall(final Hir.Call expr) {
    expr.target(expr.target().transform(this));
    expr.arguments(transformCallArguments(expr.arguments()));
    return expr;
  }

  default Hir.Argument[] transformCallArguments(final Hir.Argument[] arguments) {
    for (var i = 0; i < arguments.length; i++) {
      arguments[i] = expect(arguments[i].transform(this), Hir.Argument.class);
    }
    return arguments;
  }

  default Hir.Argument transformCallArgument(final Hir.Argument expr) {
    expr.value(expr.value().transform(this));
    return expr;
  }

  default Hir.Expression transformSpread(final Hir.Spread expr) {
    expr.value(expr.value().transform(this));
    return expr;
  }

  default Hir.Expression transformLexeme(final Hir.Lexeme expr) {
    return expr;
  }
}
