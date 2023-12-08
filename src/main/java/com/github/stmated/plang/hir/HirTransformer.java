package com.github.stmated.plang.hir;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * TODO: In general, all visits should be to a UNIQUE function, so Dec should have a DecType class that THEN have a "type"
 *        This way we can override the visit/transform of ONLY the DecType
 */
public interface HirTransformer {

  default Hir.Expression join(Hir.Expression[] values) {
    return new Hir.Expressions(values);
  }

  default Hir.Expression none() {
    return null;
  }

  default Hir.Expression convert(Hir.Expression expr) {
    return expr;
  }

  default <T extends Hir.Expression> T[] batch(T[] expressions, Class<T> clazz) {

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

  default <R extends Hir.Expression> R expect(Hir.Expression expr, Class<R> clazz) {
    if (clazz.isAssignableFrom(expr.getClass())) {
      return (R) expr;
    } else {
      throw new IllegalArgumentException(STR."Expected '\{expr}' to be a '\{clazz}'");
    }
  }

  default Hir.Expression transformFunction(Hir.Function expr) {

    expr.signature(expect(expr.signature().transform(this), Hir.FunctionSignature.class));
    expr.body(expect(expr.body().transform(this), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformExpressions(Hir.Expressions expr) {
    expr.children(batch(expr.children(), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformArgument(Hir.Argument expr) {
    expr.value(expr.value().transform(this));
    return expr;
  }

  default Hir.Expression transformArray(Hir.Array expr) {
    expr.elementType(transformArrayElementType(expr.elementType()));
    expr.length(transformArrayLength(expr.length()));
    expr.elements(transformArrayElements(expr.elements()));

    return expr;
  }

  default Hir.Expression transformArrayElementType(Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformArrayLength(Hir.Expression expr) {
    if (expr == null) {
      return null;
    }

    return expr.transform(this);
  }

  default Hir.Expression[] transformArrayElements(Hir.Expression[] exprs) {
    return batch(exprs, Hir.Expression.class);
  }

  default Hir.Expression transformArrayAccess(Hir.ArrayAccess expr) {
    expr.target(expr.target().transform(this));
    expr.accessor(expr.accessor().transform(this));
    return expr;
  }

  default Hir.Expression transformAssignment(Hir.Assignment expr) {
    final var lhs = transformAssignmentLhs(expr.lhs());
    final var rhs = transformAssignmentRhs(expr.rhs());
    return new Hir.Assignment(lhs, rhs);
  }

  default Hir.Expression transformAssignmentLhs(Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformAssignmentRhs(Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformBinaryOperation(Hir.BinaryOperation expr) {
    expr.lhs(expr.lhs().transform(this));
    expr.rhs(expr.rhs().transform(this));
    return expr;
  }

  default Hir.Expression transformBlock(Hir.Block expr) {
    expr.children(expr.children().transform(this));
    return expr;
  }

  default Hir.Expression transformConditional(Hir.Conditional expr) {
    expr.predicate(expr.predicate().transform(this));
    expr.pass(expr.pass().transform(this));
    if (expr.fail() != null) {
      expr.fail(expr.fail().transform(this));
    }
    return expr;
  }

  default Hir.Expression transformFunctionSignature(Hir.FunctionSignature expr) {
    expr.parameters(transformFunctionSignatureParameters(expr.parameters()));
    expr.returnType(transformFunctionSignatureReturnType(expr.returnType()));

    return expr;
  }

  default Hir.Parameter[] transformFunctionSignatureParameters(Hir.Parameter[] expr) {
    return batch(expr, Hir.Parameter.class);
  }

  default Hir.Expression transformFunctionSignatureReturnType(Hir.Expression expr) {
    if (expr == null) {
      return null;
    }

    return expr.transform(this);
  }

  default Hir.Expression transformIdentifier(Hir.Identifier expr) {
    return expr;
  }

  default Hir.Expression transformLabeling(Hir.Labeling expr) {
    expr.lhs(expr.lhs().transform(this));
    expr.rhs(expr.rhs().transform(this));
    return expr;
  }

  default Hir.Expression transformLiteral(Hir.Literal expr) {
    return expr;
  }

  default Hir.Expression transformLoop(Hir.Loop expr) {
    expr.body(expr.body().transform(this));
    return expr;
  }

  default Hir.Expression transformLoopBreak(Hir.LoopBreak expr) {
    if (expr.value() != null) {
      expr.value(expr.value().transform(this));
    }
    return expr;
  }

  default Hir.Expression transformLoopContinue(Hir.LoopContinue expr) {
    return expr;
  }

  default Hir.Expression transformNewByBlock(Hir.NewByBlock expr) {
    expr.target(expr.target().transform(this));
    expr.allocator(transformAllocator(expr.allocator()));
    expr.fields(transformNewByBlockFields(expr.fields()));

    return expr;
  }

  default Hir.Identifier transformAllocator(Hir.Identifier expr) {
    if (expr == null) {
      return null;
    }

    return expect(expr.transform(this), Hir.Identifier.class);
  }

  default Hir.Assignment[] transformNewByBlockFields(Hir.Assignment[] fields) {
    final var list = new ArrayList<Hir.Assignment>(fields.length);
    for (final var child : fields) {
      final var transformed = transformNewByBlockField(child);
      if (transformed != null) {
        list.add(transformed);
      }
    }

    return list.toArray(new Hir.Assignment[0]);
  }

  default Hir.Assignment transformNewByBlockField(Hir.Assignment field) {
    return expect(field.transform(this), Hir.Assignment.class);
  }

  default Hir.Expression transformNewByCtor(Hir.NewByCtor expr) {
    expr.target(expr.target().transform(this));
    expr.allocator(transformAllocator(expr.allocator()));

    if (expr.arguments() != null) {
      expr.arguments(expr.arguments().transform(this));
    }

    return expr;
  }

  default Hir.Expression transformNot(Hir.Not expr) {
    expr.expression(expr.expression().transform(this));
    return expr;
  }

  default Hir.Expression transformParameter(Hir.Parameter expr) {
    expr.lexeme(transformParameterName(expr.lexeme()));
    expr.valueType(transformParameterType(expr.valueType()));
    return expr;
  }

  default Hir.Lexeme transformParameterName(Hir.Lexeme expr) {
    return expect(expr.transform(this), Hir.Lexeme.class);
  }

  default Hir.Expression transformParameterType(Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformPath(Hir.Path expr) {
    expr.elements(batch(expr.elements(), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformRange(Hir.Range expr) {
    expr.lower(expr.lower().transform(this));
    expr.higher(expr.higher().transform(this));
    return expr;
  }

  default Hir.Expression transformProgram(Hir.Program expr) {
    expr.expressions(expr.expressions().transform(this));
    return expr;
  }

  default Hir.Expression transformReturn(Hir.Return expr) {
    expr.expression(expr.expression().transform(this));
    return expr;
  }

  default Hir.Expression transformStruct(Hir.Struct expr) {
    expr.declarations(batch(expr.declarations(), Hir.Dec.class));
    return expr;
  }

  default Hir.Expression transformTrait(Hir.Trait expr) {
    expr.children(batch(expr.children(), Hir.Expression.class));
    return expr;
  }

  default Hir.Expression transformTuple(Hir.Tuple expr) {
    expr.children(batch(expr.children(), Hir.TupleKeyValue.class));
    return expr;
  }

  default Hir.Expression transformTupleKeyValue(Hir.TupleKeyValue expr) {
    if (expr.key() != null) {
      expr.key(expect(expr.key().transform(this), Hir.Identifier.class));
    }

    expr.value(expr.value().transform(this));
    return expr;
  }

  default Hir.Expression transformTyExpr(Hir.TyExpr expr) {
    return expr;
  }

  default Hir.Expression transformDec(Hir.Dec expr) {
    expr.lexeme(transformDecName(expr.lexeme));
    expr.valueType(transformDecType(expr.valueType()));
    return expr;
  }

  default Hir.Lexeme transformDecName(Hir.Lexeme expr) {
    return expect(expr.transform(this), Hir.Lexeme.class);
  }

  default Hir.Expression transformDecType(Hir.Expression expr) {
    return expr.transform(this);
  }

  default Hir.Expression transformCall(Hir.Call expr) {
    expr.target(expr.target().transform(this));
    expr.arguments(batch(expr.arguments(), Hir.Argument.class));
    return expr;
  }

  default Hir.Expression transformLexeme(Hir.Lexeme expr) {
    return expr;
  }
}
