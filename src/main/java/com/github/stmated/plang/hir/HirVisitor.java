package com.github.stmated.plang.hir;

public interface HirVisitor {

  default void visitExpressions(Hir.Expressions expr) {
    for (final var child : expr.children()) {
      child.visit(this);
    }
  }

  default void visitArgument(Hir.Argument expr) {
    expr.value().visit(this);
  }

  default void visitArray(Hir.Array expr) {
    expr.elementType().visit(this);

    if (expr.length() != null) {
      expr.length().visit(this);
    }

    for (final var element : expr.elements()) {
      element.visit(this);
    }
  }

  default void visitArrayAccess(Hir.ArrayAccess expr) {
    expr.target().visit(this);
    expr.accessor().visit(this);
  }

  default void visitAssignment(Hir.Assignment expr) {
    visitAssignmentLhs(expr.lhs());
    visitAssignmentRhs(expr.rhs());
  }

  default void visitAssignmentLhs(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitAssignmentRhs(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitBinaryOperation(Hir.BinaryOperation expr) {
    expr.lhs().visit(this);
      expr.rhs().visit(this);
  }

  default void visitBlock(Hir.Block expr) {
    expr.children().visit(this);
  }

  default void visitConditional(Hir.Conditional expr) {
    expr.predicate().visit(this);
    expr.pass().visit(this);
    if (expr.fail() != null) {
      expr.fail().visit(this);
    }
  }

  default void visitFunction(Hir.Function expr) {
    expr.signature().visit(this);
    visitFunctionBody(expr.body());
  }

  default void visitFunctionSignature(Hir.FunctionSignature expr) {
    for (final var parameter : expr.parameters()) {
      parameter.visit(this);
    }

    if (expr.returnType() != null) {
      expr.returnType().visit(this);
    }
  }

  default void visitFunctionBody(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitIdentifier(Hir.Identifier expr) {
  }

  default void visitLabeling(Hir.Labeling expr) {
    expr.lhs().visit(this);
      expr.rhs().visit(this);
  }

  default void visitLiteral(Hir.Literal expr) {

  }

  default void visitLoop(Hir.Loop expr) {
    expr.body().visit(this);
  }

  default void visitLoopBreak(Hir.LoopBreak expr) {
    if (expr.value() != null) {
      expr.value().visit(this);
    }
  }

  default void visitLoopContinue(Hir.LoopContinue expr) {

  }

  default void visitNewByBlock(Hir.NewByBlock expr) {
    expr.target().visit(this);
    visitAllocator(expr.allocator());

    for (final var field : expr.fields()) {
      field.visit(this);
    }
  }

  default void visitNewByCtor(Hir.NewByCtor expr) {
    expr.target().visit(this);
    visitAllocator(expr.allocator());

    if (expr.arguments() != null) {
      expr.arguments().visit(this);
    }
  }

  default void visitAllocator(Hir.Identifier expr) {
    if (expr != null) {
      expr.visit(this);
    }
  }

  default void visitNot(Hir.Not expr) {
    expr.expression().visit(this);
  }

  default void visitParameter(Hir.Parameter expr) {
    visitParameterName(expr.lexeme());
    visitParameterType(expr.valueType());
  }

  default void visitParameterName(Hir.Lexeme expr) {
    expr.visit(this);
  }

  default void visitParameterType(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitPath(Hir.Path expr) {
    for (final var element : expr.elements()) {
      visitPathElement(element);
    }
  }

  default void visitPathElement(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitRange(Hir.Range expr) {
    expr.lower().visit(this);
    expr.higher().visit(this);
  }

  default void visitProgram(Hir.Program expr) {
    expr.expressions().visit(this);
  }

  default void visitReturn(Hir.Return expr) {
    expr.expression().visit(this);
  }

  default void visitStruct(Hir.Struct expr) {
    for (final var item : expr.declarations()) {
      item.visit(this);
    }
  }

  default void visitTrait(Hir.Trait expr) {
    for (final var item : expr.children()) {
      item.visit(this);
    }
  }

  default void visitTuple(Hir.Tuple expr) {
    for (final var item : expr.children()) {
      item.visit(this);
    }
  }

  default void visitTupleKeyValue(Hir.TupleKeyValue expr) {
    if (expr.key() != null) {
      expr.key().visit(this);
    }

    expr.value().visit(this);
  }

  default void visitTyExpr(Hir.TyExpr expr) {

  }

  default void visitDec(Hir.Dec expr) {
    visitDecName(expr.lexeme());
    visitDecType(expr.valueType());
  }

  default void visitDecName(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitDecType(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitCall(Hir.Call expr) {
    expr.target().visit(this);
    for (final var argument : expr.arguments()) {
      argument.visit(this);
    }
  }

  default void visitReference(Hir.Reference expr) {
    expr.target().visit(this);
  }

  default void visitLexeme(Hir.Lexeme lexeme) {

  }
}
