package org.inf.ast;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;
import org.inf.lexer.TokenType;
import org.inf.ty.TyValue;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@UtilityClass
public class Ast {

  @Data
  @AllArgsConstructor
  public static class Assignment implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public String toString() {
      return lhs + " = " + rhs;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitAssignment(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Become implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitBecome(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class BinaryOperation implements Expression {

    Expression lhs;
    BinaryOperationKind kind;
    Expression rhs;

    @Override
    public String toString() {
      return "%s %s %s".formatted(lhs, kind, rhs);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitBinaryOperation(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Block implements Expression {

    Expression expression;

    @Override
    public String toString() {
      return Objects.toString(expression);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitBlock(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Bracket implements Expression, HasChildren {

    Expression[] children;

    @Override
    public String toString() {

      final var childrenString = Arrays.stream(children).map(Object::toString).collect(Collectors.joining(", "));
      return "[%s]".formatted(childrenString);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitBracket(this);
    }
  }

//  /**
//   * This should be removed in favor of a more agnostic AST stage, and convert brackets based on context in HIR stage.
//   * This will require less backtracking, since we will not actually care what it is inside the AST stage.
//   */
//  @Data
//  @AllArgsConstructor
//  public static class BracketAccess implements Expression {
//
//    Expression target;
//    Bracket accessor;
//
//    @Override
//    public String toString() {
//      return target + "[" + accessor + "]";
//    }
//
//    @Override
//    public <R, V extends AstVisitor<R>> R visit(V visitor) {
//      return visitor.visitBracketAccess(this);
//    }
//  }

  /// Represents things like `func(arg1, arg2)` and `array[0]`.
  /// Where a target has a suffix, with the suffix having some kind of relevance to the target.
  @Data
  @AllArgsConstructor
  public static class PostfixExpression implements Expression {

    Expression target;
    Expression suffix;

    @Override
    public String toString() {
      return "%s%s".formatted(this.target, this.suffix);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitPostfixExpression(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Juxtaposition implements Expression {

    Expression target;
    Expression[] arguments;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitJuxtaposition(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Partial<E extends Expression> implements Expression {

    E expression;

    @Override
    public String toString() {
      return "~%s".formatted(this.expression);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitPartial(this);
    }
  }

  /// Put as suffix on another expression to mean "bubble any error from this up the call stack"
  @Data
  @AllArgsConstructor
  public static class Bubble implements Expression {

    Expression expression;

    @Override
    public String toString() {
      return "!%s".formatted(this.expression);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitBubble(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Callable implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitCallable(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Comment implements Expression {

    String content;

    @Override
    public String toString() {
      return "/*" + content + " */";
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitComment(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class CompTime implements Expression {

    Expression target;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitCompTime(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Conditional implements Expression {

    Expression predicate;
    Expression pass;
    Expression fail;

    @Override
    public String toString() {
      return "if %s then %s else %s".formatted(predicate, pass, fail);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitConditional(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class DotAccess implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public String toString() {
      return "%s.%s".formatted(lhs, rhs);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitDotAccess(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Export implements Expression {

    Expression exported;
    boolean isDefault;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitExport(this);
    }
  }

  public interface Expression {
    <R, V extends AstVisitor<R>> R visit(V visitor);
  }

  public interface HasChildren {
    Expression[] children();
  }

  @Data
  @AllArgsConstructor
  public static class Expressions implements Expression, HasChildren {

    Expression[] children;

    @Override
    public String toString() {
      return Arrays.toString(children);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitExpressions(this);
    }

    public static Expression from(final List<Expression> collection) {

      if (collection.isEmpty()) {
        return null;
      }

      if (collection.size() == 1) {
        return collection.getFirst();
      } else {
        return new Ast.Expressions(collection.toArray(new Expression[0]));
      }
    }
  }

  @Data
  @AllArgsConstructor
  public static class Lexeme implements Expression {

    String name;

    @Override
    public String toString() {
      return name;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLexeme(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Import implements Expression {

    ImportCapable path;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImport(this);
    }
  }

  public interface ImportCapable extends Expression {
  }

  @Data
  @AllArgsConstructor
  public static class ImportPath implements Expression, ImportCapable {

    ImportCapable lhs;
    ImportCapable rhs;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImportPath(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class ImportPathAlias implements Expression, ImportCapable {

    Lexeme alias;
    ImportCapable target;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImportPathAlias(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class ImportPathGroup implements Expression, ImportCapable {

    ImportCapable[] items;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImportPathGroup(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class ImportPathIdentifier implements Expression, ImportCapable {

    Lexeme lexeme;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImportPathIdentifier(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class ImportPathWildcard implements Expression, ImportCapable {

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImportPathWildcard(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class In implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public String toString() {
      return lhs + " IN " + rhs;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitIn(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Infer implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitInfer(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Labeling implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public String toString() {
      return lhs + ":" + rhs;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLabeling(this);
    }
  }

  /**
   * TODO: Probably not good, since it relies on the data types of Java and not the actual target language
   */
  @Data
  @AllArgsConstructor
  public static class Literal implements Expression {

    String content;
    TyValue ty;

    @Override
    public String toString() {
      return content + ":" + ty;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLiteral(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Loop implements Expression {

    Expression body;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLoop(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class LoopDoWhile implements Expression {

    Expression body;
    Expression predicate;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLoopDoWhile(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class LoopFor implements Expression {

    Expression head;
    Expression block;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLoopFor(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class LoopWhile implements Expression {

    Expression predicate;
    Expression body;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitLoopWhile(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Match implements Expression {

    Expression target;
    Expressions children;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitMatch(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Negate implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitNegate(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class New implements Expression {

    Expression target;
    Lexeme allocator;
    Expression arguments;

    @Override
    public String toString() {
      return "new " + allocator + " " + target + "(" + arguments + ")";
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitNew(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Comma implements Expression {

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitComma(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class NoOp implements Expression {

    @Override
    public String toString() {
      return "NoOp";
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitNoOp(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Not implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitNot(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Paren implements Expression {

    @Nullable
    Expression expression;

    @Override
    public String toString() {
      return (this.expression == null) ? "()" : "(%s)".formatted(this.expression);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitParen(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Program implements Expression {

    Expression children;

    @Override
    public String toString() {
      return Objects.toString(children);
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitProgram(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Range implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitRange(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Rest implements Expression {

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitRest(this);
    }

    @Override
    public String toString() {
      return "...";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Return implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitReturn(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Spread implements Expression {

    @Nullable
    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitSpread(this);
    }

    @Override
    public String toString() {
      return "..." + expression;
    }
  }

  @Data
  @AllArgsConstructor
  public static class StaticAccess implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public String toString() {
      return lhs + "::" + rhs;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitStaticAccess(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Struct implements Expression {

    Block block;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitStruct(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Then implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitThen(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Trait implements Expression {

    Block block;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitTrait(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Type implements Expression {

    Lexeme lexeme;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitType(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class TypePlaceholder implements Expression {

    Lexeme lexeme;

    @Override
    public String toString() {
      return "$" + lexeme;
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitTypePlaceholder(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class VariableDeclaration implements Expression {

    Lexeme lexeme;
    MutabilityKind mutabilityKind;
    Expression type;
    boolean ref;

    @Override
    public String toString() {
      return mutabilityKind + " " + (ref ? "ref " : "") + lexeme + ((type != null) ? (": " + type) : "");
    }

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitVariableDeclaration(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class VariableSink implements Expression {

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitVariableSink(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Where implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitWhere(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class With implements Expression {

    Expression argument;
    Block block;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitWith(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Yield implements Expression {

    Expression expression;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitYield(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Impl implements Expression {

    Lexeme traitLexeme;
    Expression forExpression;
    Block block;
    Expression with;

    @Override
    public <R, V extends AstVisitor<R>> R visit(final V visitor) {
      return visitor.visitImpl(this);
    }
  }

  public enum BinaryOperationKind {

    LTE,
    GTE,
    EQUALS,
    NOT_EQUALS,
    LT,
    GT,
    IS,

    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULUS,
    REMAINDER,
    POW,
    BIT_SHIFT_LEFT,
    BIT_SHIFT_RIGHT,

    DIVIDE_ASSIGNMENT,
    MULTIPLY_ASSIGNMENT,
    ADDITION_ASSIGNMENT,
    SUBTRACTION_ASSIGNMENT,

    OR,
    AND,

    BIT_OR,
    BIT_AND;

    public static BinaryOperationKind fromTokenType(final TokenType tokenType) {

      return switch (tokenType) {
        case LTE -> BinaryOperationKind.LTE;
        case GTE -> BinaryOperationKind.GTE;
        case EQUALS -> BinaryOperationKind.EQUALS;
        case NOT_EQUALS -> BinaryOperationKind.NOT_EQUALS;
        case LT -> BinaryOperationKind.LT;
        case GT -> BinaryOperationKind.GT;
        case IS -> BinaryOperationKind.IS;

        case ADD -> BinaryOperationKind.ADD;
        case SUBTRACT -> BinaryOperationKind.SUBTRACT;
        case MULTIPLY -> BinaryOperationKind.MULTIPLY;
        case DIVIDE -> BinaryOperationKind.DIVIDE;
        case MODULUS -> BinaryOperationKind.MODULUS;
        case REMAINDER -> BinaryOperationKind.REMAINDER;
        case POW -> BinaryOperationKind.POW;

        case ADDITION_ASSIGNMENT -> BinaryOperationKind.ADDITION_ASSIGNMENT;
        case SUBTRACTION_ASSIGNMENT -> BinaryOperationKind.SUBTRACTION_ASSIGNMENT;
        case MULTIPLY_ASSIGNMENT -> BinaryOperationKind.MULTIPLY_ASSIGNMENT;
        case DIVIDE_ASSIGNMENT -> BinaryOperationKind.DIVIDE_ASSIGNMENT;

        case BIT_SHIFT_LEFT -> BinaryOperationKind.BIT_SHIFT_LEFT;
        case BIT_SHIFT_RIGHT -> BinaryOperationKind.BIT_SHIFT_RIGHT;

        case OR -> BinaryOperationKind.OR;
        case AND -> BinaryOperationKind.AND;

        case BIT_OR -> BinaryOperationKind.BIT_OR;
        case BIT_AND -> BinaryOperationKind.BIT_AND;

        default -> throw new IllegalArgumentException("TokenType '%s' is not a binary operator");
      };
    }
  }

  public enum MutabilityKind {

    Mutable,
    Immutable
  }
}
