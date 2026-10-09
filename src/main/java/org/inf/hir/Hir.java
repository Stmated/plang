package org.inf.hir;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.ty.*;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;

import java.util.Arrays;
import java.util.Objects;

@UtilityClass
public class Hir {

  public interface Expression {
    Ty ty();

    void visit(HirVisitor visitor);

    Hir.Expression transform(HirTransformer transformer);
  }

  /// A source type constraint, not a runtime expression or an inferred binding type.
  public record DynamicTy(@Nonnull Ty ty, Expression expression) {

    public DynamicTy {
      Objects.requireNonNull(ty);
    }

    public DynamicTy(final Ty ty) {
      this(ty, null);
    }

    public DynamicTy(final Expression expression) {
      this(Ty.INFER, Objects.requireNonNull(expression));
    }

    /// Refresh expression-backed constraints; static constraints need no resolution.
    public DynamicTy resolve(final java.util.function.Function<Expression, Ty> resolver) {
      if (expression == null) {
        return this;
      }
      final var resolved = Objects.requireNonNullElse(resolver.apply(expression), Ty.INFER);
      return resolved == ty ? this : new DynamicTy(resolved, expression);
    }

    public void visit(final HirVisitor visitor) {
      if (expression != null) {
        visitor.visitChild(expression);
      }
    }

    public DynamicTy transform(final HirTransformer transformer) {
      if (expression == null) {
        return this;
      }
      final var transformed = Objects.requireNonNull(expression.transform(transformer));
      return transformed == expression ? this : new DynamicTy(ty, transformed);
    }

    @Override
    public String toString() {
      return expression == null ? ty.toShortString() : expression.toString();
    }
  }

  public interface ExpressionsOwner<Self extends Expression> {
    Expression[] children();

    Self children(Expression[] expressions);
  }

  /// TODO: Look into if this can be deleted somehow
  ///   So that each specific location where multiple expressions might be needed, there is a more specialized expression.
  ///   This might be needed to go all the way with "everything is one expression with one return type"
  @Data
  @AllArgsConstructor
  public static class Expressions implements Expression, ExpressionsOwner<Expressions> {

    Expression[] children;

    /// TODO: This should likely be fully derived from the child expressions, more specifically the last one.
    ///       There should not be any real need to cache a `ty` here, unless it turns out to be very expensive.
    Ty ty;

    /// Only compiler-generated sequencing may discard unreachable suffixes.
    /// TODO: Would be preferable if this could be done some other way, with a specific "DEADEND-allowed container" node.
    boolean generated;

    public Expressions(final Expression[] children, final Ty ty) {
      this(children, ty, false);
    }

    public Expressions(final Expression[] children) {
      this(children, null);
    }

    @Override
    public String toString() {
      return String.join("; ", Arrays.stream(children()).map(Objects::toString).toList());
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitExpressions(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformExpressions(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Argument implements Expression {

    final Lexeme label;
    @Nonnull
    Expression value;

    @Override
    public Ty ty() {
      return value.ty();
    }

    @Override
    public String toString() {
      return "%s%s".formatted(label == null ? "" : "%s=".formatted(label), value);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitCallArgument(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformCallArgument(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Spread implements Expression {

    @Nonnull
    Expression value;

    @Override
    public Ty ty() {
      return value.ty();
    }

    @Override
    public String toString() {
      return "...%s".formatted(value);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitSpread(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformSpread(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Array implements Expression {

    @Nonnull
    Expression[] elements;
    @Nonnull
    DynamicTy elementType;
    Expression length;
    Ty ty;
    Ty arrayTy;

    @Override
    public String toString() {

      final var childrenStrings = Arrays.stream(elements()).map(Object::toString).toList();
      final var childrenString = String.join(", ", childrenStrings);

      return "[%s;%s;%s]".formatted(childrenString, elementType(), length());
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitArray(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformArray(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class ArrayAccess implements Expression {

    Expression target;
    Expression accessor;
    Ty ty;
    Ty indexedTy;

    @Override
    public String toString() {
      return "%s[%s]".formatted(target, accessor);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitArrayAccess(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformArrayAccess(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Assignment implements Expression {

    Expression lhs;
    Expression rhs;
    Ty ty;

    public Assignment(Expression lhs, Expression rhs) {
      this(lhs, rhs, null);
    }

    @Override
    public String toString() {
      if (lhs instanceof final Hir.Dec dec) {
        return "%s = %s".formatted(dec.toShortString(), rhs);
      } else {
        return "%s = %s".formatted(lhs, rhs);
      }
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitAssignment(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformAssignment(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class CompoundAssignment implements Expression {

    Expression target;
    BinaryOperationKind kind;
    Expression rhs;
    Ty ty;

    public CompoundAssignment(Expression target, BinaryOperationKind kind, Expression rhs) {
      this(target, kind, rhs, null);
    }

    @Override
    public void visit(HirVisitor visitor) {
      visitor.visitCompoundAssignment(this);
    }

    @Override
    public Expression transform(HirTransformer transformer) {
      return transformer.transformCompoundAssignment(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Convert implements Expression {

    @Nonnull
    Expression expression;
    @Nonnull
    final Ty targetTy;

    @Override
    public Ty ty() {
      return expression.ty() == Ty.DEADEND ? Ty.DEADEND : targetTy;
    }

    @Override
    public void visit(HirVisitor visitor) {
      visitor.visitConvert(this);
    }

    @Override
    public Expression transform(HirTransformer transformer) {
      return transformer.transformConvert(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class BinaryOperation implements Expression {

    Expression lhs;
    BinaryOperationKind kind;
    Expression rhs;
    Ty ty;

    public BinaryOperation(Expression lhs, BinaryOperationKind kind, Expression rhs) {
      this.lhs = lhs;
      this.kind = kind;
      this.rhs = rhs;
    }

    @Override
    public String toString() {
      return "%s %s %s".formatted(this.lhs(), kind, this.rhs());
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitBinaryOperation(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformBinaryOperation(this);
    }
  }

  public enum BinaryOperationKind {

    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULUS,
    REMAINDER,
    POW,

    BIT_SHIFT_LEFT,
    BIT_SHIFT_RIGHT,

    LTE,
    GTE,

    LT,
    GT,

    EQUALS,
    NOT_EQUALS,
    IS,

    OR,
    AND,

    BIT_OR,
    BIT_AND;

    public boolean isPredicate() {
      return this == LTE || this == GTE || this == LT || this == GT || this == EQUALS || this == NOT_EQUALS || this == IS || this == OR || this == AND;
    }

    public boolean isShortCircuiting() {
      return this == OR || this == AND;
    }
  }

  @Data
  @AllArgsConstructor
  public static class Block implements Expression {

    Expression children;
    Ty ty;

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitBlock(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformBlock(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Call implements Expression {

    Expression target;
    @Nonnull
    Argument[] arguments;
    boolean partial;
    Ty ty;

    public Call(final Expression target, final Argument[] arguments) {
      this.target = target;
      this.arguments = Objects.requireNonNull(arguments);
    }

    @Override
    public String toString() {
      final var argumentStrings = String.join(", ", Arrays.stream(arguments).map(Argument::toString).toList());
      return "%s%s(%s)".formatted(target, partial ? "~" : "", argumentStrings);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitCall(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformCall(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Conditional implements Expression {

    Expression predicate;
    Expression pass;
    Expression fail;
    Ty ty;

    public Conditional(Expression predicate, Expression pass, Expression fail) {
      this.predicate = predicate;
      this.pass = pass;
      this.fail = fail;
    }

    @Override
    public String toString() {
      return "if (%s) then {%s} else {%s}".formatted(this.predicate(), this.pass(), this.fail());
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitConditional(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformConditional(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Function implements Expression {

    FunctionSignature signature;
    Expression body;

    @Override
    public TyFn ty() {
      return signature.ty();
    }

    @Override
    public String toString() {
      return "%s => {...}".formatted(signature);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitFunction(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformFunction(this);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Function function = (Function) o;
      return Objects.equals(signature, function.signature) && Objects.equals(body, function.body);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class FunctionSignature implements Expression {

    @Nonnull
    Parameter[] parameters;
    boolean vararg;
    @Nonnull
    DynamicTy returnTypeAnnotation;
    TyFn ty;

    public FunctionSignature(@Nonnull Parameter[] parameters, boolean vararg, @Nonnull DynamicTy returnTypeAnnotation) {
      this.parameters = parameters;
      this.vararg = vararg;
      this.returnTypeAnnotation = Objects.requireNonNull(returnTypeAnnotation);
    }

    @Override
    public String toString() {

      final var parameterStrings = Arrays.stream(parameters()).map(Parameter::toString).toList();
      return "(%s%s): %s".formatted(String.join(", ", parameterStrings), vararg() ? ", ..." : "", returnTypeAnnotation);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitFunctionSignature(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformFunctionSignature(this);
    }
  }

  @Data
  @RequiredArgsConstructor
  public static class Lexeme implements Expression {

    @Nonnull
    final String name;

    @Override
    public Ty ty() {
      return Ty.VOID;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitLexeme(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformLexeme(this);
    }
  }

  @Data
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class BuiltInTy implements Expression {

    @Nonnull
    final String name;
    @Nonnull
    final Ty ty;

    /// Like `Tys.fromString`, returns null for an unrecognized built-in name.
    @Nullable
    public static BuiltInTy fromString(final String name, final MachineTarget machineTarget) {
      final var ty = Tys.fromString(name, machineTarget);
      return ty == null ? null : new BuiltInTy(name, ty);
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitBuiltInTy(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformBuiltInTy(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Identifier implements Expression {

    @Nonnull
    final String name;
    Expression target;

    @Override
    public Ty ty() {
      return (this.target != null) ? Tys.getBindingTy(this.target) : null;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitIdentifier(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformIdentifier(this);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Identifier that = (Identifier) o;
      return Objects.equals(name, that.name)
        && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Labeling implements Expression {

    @Nonnull
    Hir.Expression lhs;
    @Nonnull
    Hir.Expression rhs;
    Ty ty;

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitLabeling(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformLabeling(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Literal implements Expression {

    String content;
    TyValue ty;

    @Override
    public String toString() {

      if (ty instanceof final TyValueNumber num) {
        return switch (num.getValueKind()) {
          case INTEGER -> switch (num.width().value()) {
            case 32 -> content;
            default -> content + (num.signed() ? "s" : "u") + num.radix();
          };
          case FLOAT -> "%sf".formatted(content);
          case DOUBLE -> "%sm".formatted(content);
          case DECIMAL -> "%sd".formatted(content);
          default -> content;
        };
      } else if (ty instanceof TyValueString) {
        return "\"%s\"".formatted(content);
      } else {
        return "%s: %s".formatted(content, ty.toShortString());
      }
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitLiteral(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformLiteral(this);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Loop implements Expression {

    Expression body;
    Ty ty;

    public Loop(Expression body) {
      this.body = body;
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitLoop(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformLoop(this);
    }
  }

  /**
   * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
   * Are there benefits to being able to represent a "break" further down the chain?
   */
  @Data
  public static class LoopBreak implements Expression {

    Expression value;
    //Ty ty;

    @Override
    public Ty ty() {
      return TyStruct.DEADEND;
    }

    public LoopBreak(Expression value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "break%s".formatted(this.value() == null ? "" : " %s".formatted(this.value()));
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitLoopBreak(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformLoopBreak(this);
    }
  }

  /**
   * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
   * Are there benefits to being able to represent a "continue" further down the chain?
   */
  @Data
  @AllArgsConstructor
  public static class LoopContinue implements Expression {

    @Override
    public Ty ty() {
      return Ty.DEADEND;
    }

    @Override
    public String toString() {
      return "continue";
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitLoopContinue(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformLoopContinue(this);
    }
  }

  public enum MutabilityKind {

    MUTABLE,
    IMMUTABLE,
    CONSTANT
  }

  @Data
  @AllArgsConstructor
  public static class NewByBlock implements Expression {

    Expression target;
    Identifier allocator;
    Assignment[] fields;
    Ty ty;

    @Override
    public String toString() {
      return "new %s{%s}".formatted(target, Arrays.toString(fields));
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitNewByBlock(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformNewByBlock(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class NewByCtor implements Expression {

    Expression target;
    Identifier allocator;
    Expression arguments;
    Ty ty;

    @Override
    public String toString() {
      return "new %s(%s)".formatted(target, arguments);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitNewByCtor(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformNewByCtor(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Not implements Expression {

    Expression expression;
    Ty ty;

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitNot(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformNot(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Parameter implements Expression {

    @Nonnull
    Hir.Lexeme lexeme;
    @Nonnull
    DynamicTy typeAnnotation;
    boolean vararg;
    Ty resolvedTy;

    @Override
    public Ty ty() {
      return Ty.VOID;
    }

    @Override
    public String toString() {
      return "%s:%s".formatted(lexeme, typeAnnotation);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitParameter(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformParameter(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class DotAccess implements Expression {

    @Nonnull
    Expression target;
    @Nonnull
    final String name;
    Ty ty;

    public DotAccess(final Expression target, final String name) {
      this(target, name, null);
    }

    public Ty memberTy() {
      return Tys.getMemberTy(this);
    }

    @Override
    public String toString() {
      return "%s.%s".formatted(target, name);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitDotAccess(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformDotAccess(this);
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Program implements Expression {
    Expression expressions;
    Ty ty;

    public Program(final Expression expressions) {
      this(expressions, null);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitProgram(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformProgram(this);
    }
  }

  @Data
  //@AllArgsConstructor
  public static class Range implements Expression {
    Expression lower;
    Expression higher;
    Ty ty;
    Ty rangeTy;

//    @Override
//    public Ty ty() {
//      return null;
//    }

    public Range(Expression lower, Expression higher) {
      this.lower = lower;
      this.higher = higher;
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitRange(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformRange(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Return implements Expression {

    Expression expression;

    @Override
    public Ty ty() {
      return Ty.DEADEND;
    }

    @Override
    public String toString() {
      return "return %s".formatted(expression);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitReturn(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformReturn(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class DeadEnd implements Expression {

    Expression expression;

    @Override
    public Ty ty() {
      return Ty.DEADEND;
    }

    @Override
    public String toString() {
      return "DeadEnd %s".formatted(expression);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitDeadEnd(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformDeadEnd(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Struct implements Expression {

    Dec[] declarations;
    Ty ty;

    @Override
    public String toString() {
      return "struct {%s}".formatted(Arrays.toString(declarations));
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitStruct(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformStruct(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Trait implements Expression {
    Expression[] children;
    Ty ty;

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitTrait(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformTrait(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Tuple implements Expression {

    TupleEntry[] children;
    Ty ty;
    /// Destination layout of a fresh contextual construction; children remain in source order.
    TyStruct contextualType;

    public Tuple(final TupleEntry[] children, final Ty ty) {
      this(children, ty, null);
    }

    @Override
    public String toString() {
      final var contents = String.join(", ", Arrays.stream(children).map(TupleEntry::toString).toList());
      return "(%s%s)".formatted(contents, children.length == 1 ? "," : "");
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitTuple(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformTuple(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class TupleEntry implements Expression {

    final Lexeme label;
    @Nonnull
    Hir.Expression value;
    final boolean typeLabel;

    public TupleEntry(final Lexeme label, final Hir.Expression value) {
      this(label, value, label != null);
    }

    @Override
    public Ty ty() {
      return value.ty();
    }

    @Override
    public String toString() {
      return "%s%s".formatted(label == null ? "" : ("%s%s".formatted(label, typeLabel ? ":" : "=")), value);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitTupleEntry(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformTupleEntry(this);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Dec implements Expression {

    Lexeme lexeme;
    MutabilityKind mutabilityKind;
    @Nonnull
    DynamicTy typeAnnotation;
    Ty resolvedTy;

    public Dec(final Lexeme lexeme, final MutabilityKind mutabilityKind, final DynamicTy typeAnnotation) {
      this(lexeme, mutabilityKind, typeAnnotation, null);
    }

    public Ty ty() {
      return Ty.VOID;
    }

    @Override
    public String toString() {
      return "%s: %s".formatted(toShortString(), typeAnnotation);
    }

    public String toShortString() {
      final var mutName = switch (mutabilityKind) {
        case IMMUTABLE -> "val";
        case MUTABLE -> "var";
        case CONSTANT -> "const";
      };

      return "%s %s".formatted(mutName, lexeme);
    }

    @Override
    public void visit(final HirVisitor visitor) {
      visitor.visitDec(this);
    }

    @Override
    public Expression transform(final HirTransformer transformer) {
      return transformer.transformDec(this);
    }
  }
}
