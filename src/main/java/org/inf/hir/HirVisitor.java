package org.inf.hir;

public interface HirVisitor {

  /** Entry point for child traversal; specialized visitors may restrict reachability. */
  default void visitChild(Hir.Expression expr) {
    expr.visit(this);
  }

  default void visitExpressions(Hir.Expressions expr) {
    for (final var child : expr.children()) {
      visitChild(child);
    }
  }

  default void visitArgument(Hir.Argument expr) {
    visitChild(expr.value());
  }

  default void visitArray(Hir.Array expr) {
    visitChild(expr.elementType());

    if (expr.length() != null) {
      visitChild(expr.length());
    }

    for (final var element : expr.elements()) {
      visitChild(element);
    }
  }

  default void visitArrayAccess(Hir.ArrayAccess expr) {
    visitChild(expr.target());
    visitChild(expr.accessor());
  }

  default void visitAssignment(Hir.Assignment expr) {
    visitAssignmentLhs(expr.lhs());
    visitAssignmentRhs(expr.rhs());
  }

  default void visitAssignmentLhs(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitAssignmentRhs(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitBinaryOperation(Hir.BinaryOperation expr) {
    visitChild(expr.lhs());
    visitChild(expr.rhs());
  }

  default void visitCompoundAssignment(Hir.CompoundAssignment expr) {
    visitChild(expr.target());
    visitChild(expr.rhs());
  }

  default void visitBlock(Hir.Block expr) {
    visitChild(expr.children());
  }

  default void visitConditional(Hir.Conditional expr) {
    visitChild(expr.predicate());
    visitChild(expr.pass());
    if (expr.fail() != null) {
      visitChild(expr.fail());
    }
  }

  default void visitFunction(Hir.Function expr) {
    visitChild(expr.signature());
    visitFunctionBody(expr.body());
  }

  default void visitFunctionSignature(Hir.FunctionSignature expr) {
    for (final var parameter : expr.parameters()) {
      visitChild(parameter);
    }

    if (expr.returnType() != null) {
      visitChild(expr.returnType());
    }
  }

  default void visitFunctionBody(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitIdentifier(Hir.Identifier expr) {
  }

  default void visitLabeling(Hir.Labeling expr) {
    visitChild(expr.lhs());
    visitChild(expr.rhs());
  }

  default void visitLiteral(Hir.Literal expr) {

  }

  default void visitLoop(Hir.Loop expr) {
    visitChild(expr.body());
  }

  default void visitLoopBreak(Hir.LoopBreak expr) {
    if (expr.value() != null) {
      visitChild(expr.value());
    }
  }

  default void visitLoopContinue(Hir.LoopContinue expr) {

  }

  default void visitNewByBlock(Hir.NewByBlock expr) {
    visitChild(expr.target());
    visitAllocator(expr.allocator());

    for (final var field : expr.fields()) {
      visitChild(field);
    }
  }

  default void visitNewByCtor(Hir.NewByCtor expr) {
    visitChild(expr.target());
    visitAllocator(expr.allocator());

    if (expr.arguments() != null) {
      visitChild(expr.arguments());
    }
  }

  default void visitAllocator(Hir.Identifier expr) {
    if (expr != null) {
      visitChild(expr);
    }
  }

  default void visitNot(Hir.Not expr) {
    visitChild(expr.expression());
  }

  default void visitParameter(Hir.Parameter expr) {
    visitParameterName(expr.lexeme());
    visitParameterType(expr.valueType());
  }

  default void visitParameterName(Hir.Lexeme expr) {
    visitChild(expr);
  }

  default void visitParameterType(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitPath(Hir.Path expr) {
    for (final var element : expr.elements()) {
      visitPathElement(element);
    }
  }

  default void visitPathElement(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitRange(Hir.Range expr) {
    visitChild(expr.lower());
    visitChild(expr.higher());
  }

  default void visitProgram(Hir.Program expr) {
    visitChild(expr.expressions());
  }

  default void visitReturn(Hir.Return expr) {
    visitChild(expr.expression());
  }

  default void visitDeadEnd(Hir.DeadEnd expr) {
    visitChild(expr.expression());
  }

  default void visitStruct(Hir.Struct expr) {
    for (final var item : expr.declarations()) {
      visitChild(item);
    }
  }

  default void visitTrait(Hir.Trait expr) {
    for (final var item : expr.children()) {
      visitChild(item);
    }
  }

  default void visitTuple(Hir.Tuple expr) {
    for (final var item : expr.children()) {
      visitChild(item);
    }
  }

  default void visitTupleKeyValue(Hir.TupleKeyValue expr) {
    if (expr.key() != null) {
      visitChild(expr.key());
    }

    visitChild(expr.value());
  }

  default void visitTyExpr(Hir.TyExpr expr) {

  }

  default void visitDec(Hir.Dec expr) {
    visitDecName(expr.lexeme());
    visitDecType(expr.valueType());
  }

  default void visitDecName(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitDecType(Hir.Expression expr) {
    visitChild(expr);
  }

  default void visitCall(Hir.Call expr) {
    visitChild(expr.target());
    for (final var argument : expr.arguments()) {
      visitChild(argument);
    }
  }

  default void visitLexeme(Hir.Lexeme lexeme) {

  }
}
