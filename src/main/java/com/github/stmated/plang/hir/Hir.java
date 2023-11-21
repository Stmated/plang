package com.github.stmated.plang.hir;

import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyValue;
import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Hir {

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Expressions implements Expression {

    final Expression[] children;
    Ty ty;

    @Override
    public String toString() {
      final var childStrings = String.join("; ", Arrays.stream(children()).map(Objects::toString).toList());
      return STR."[\{childStrings}]";
    }
  }

  public interface Expression {
    Ty ty();
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Argument implements Expression {

    final String label;
    @Nonnull
    final Hir.Expression value;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Array implements Expression {

    final Expression[] elements;
    final Expression elementType;
    final Expression length;
    Ty ty;

    @Override
    public String toString() {

      final var childrenStrings = Arrays.stream(elements()).map(Object::toString).toList();
      final var childrenString = String.join(", ", childrenStrings);

      return STR."[\{childrenString};\{elementType()};\{length()}]";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class ArrayAccess implements Expression {

    final Expression target;
    final Expression accessor;
    Ty ty;

    @Override
    public String toString() {
      return STR."\{target}[\{accessor}]";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Assignment implements Expression {

    final Expression lhs;
    final Expression rhs;
    Ty ty;

    @Override
    public String toString() {
      return STR."\{lhs} = \{rhs}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class BinaryOperation implements Expression {

    final Expression lhs;
    final BinaryOperationKind kind;
    final Expression rhs;
    Ty ty;

    @Override
    public String toString() {
      return STR."\{this.lhs()} \{kind} \{this.rhs()}";
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
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Block implements Expression {

    final Expression children;
    Ty ty;

    @Override
    public Ty ty() {

      if (ty != null) {
        return ty;
      }

      return children().ty();
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Call implements Expression {

    final Expression target;
    final Argument[] arguments;
    final boolean partial;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Conditional implements Expression {

    final Expression predicate;
    final Expression pass;
    final Expression fail;
    Ty ty;

    @Override
    public String toString() {
      return STR."if (\{this.predicate()}) then {\{this.pass()}} else {\{this.fail()}}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Function implements Expression {

    final FunctionSignature signature;
    final Expression body;
    Ty ty;

    @Override
    public String toString() {
      return STR."\{signature} => \{body}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class FunctionSignature implements Expression {

    @Nonnull
    final Parameter[] parameters;
    final boolean vararg;
    final Expression returnType;
    Ty ty;

    @Override
    public String toString() {

      final var parameterStrings = Arrays.stream(parameters()).map(Parameter::toString).toList();
      return STR."(\{String.join(", ", parameterStrings)}\{vararg() ? "..." : ""}): \{returnType}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Identifier implements Expression {

    @Nonnull
    final String name;
    Ty ty;

    @Override
    public String toString() {
      return name;
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Labeling implements Expression {

    @Nonnull
    final Hir.Expression lhs;
    @Nonnull
    final Hir.Expression rhs;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  public static class Literal implements Expression {

    String content;
    TyValue ty;

    @Override
    public String toString() {
      return STR."\{content}: \{ty.toShortString()}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Loop implements Expression {

    final Expression body;
    Ty ty;
  }

  /**
   * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
   *        Are there benefits to being able to represent a "break" further down the chain?
   */
  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class LoopBreak implements Expression {

    final Expression value;
    Ty ty;

    @Override
    public String toString() {
      return STR."break\{this.value() == null ? "" : STR." \{this.value()}"}";
    }
  }

  /**
   * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
   *        Are there benefits to being able to represent a "continue" further down the chain?
   */
  @Data
  @AllArgsConstructor
  public static class LoopContinue implements Expression {

    @Override
    public Ty ty() {
      return Ty.VOID;
    }

    @Override
    public String toString() {
      return "continue";
    }
  }

  public enum MutabilityKind {

    MUTABLE,
    IMMUTABLE,
    CONSTANT
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class NewByBlock implements Expression {

    final Expression target;
    final Identifier allocator;
    final Assignment[] fields;
    Ty ty;

    @Override
    public String toString() {
      return STR."new \{target}{\{Arrays.toString(fields)}}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class NewByCtor implements Expression {

    final Expression target;
    final Identifier allocator;
    final Expression arguments;
    Ty ty;

    @Override
    public String toString() {
      return STR."new \{target}(\{arguments})";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Not implements Expression {

    final Expression expression;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Parameter implements Expression {

    @Nonnull
    final Hir.Expression identifier;
    @Nonnull
    final Hir.Expression type;
    final boolean vararg;
    Ty ty;

    @Override
    public String toString() {
      return STR."\{identifier}:\{type}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Path implements Expression {

    @Nonnull
    final Expression[] elements;
    Ty ty;

    @Override
    public String toString() {
      return Arrays.toString(elements);
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Program implements Expression {
    final Expression expressions;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Range implements Expression {
    final Expression lower;
    final Expression higher;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Return implements Expression {

    final Expression expression;
    Ty ty;

    @Override
    public String toString() {
      return STR."return \{expression}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Struct implements Expression {

    final VariableDeclaration[] declarations;
    Ty ty;

    @Override
    public String toString() {
      return STR."struct {\{Arrays.toString(declarations)}}";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Trait implements Expression {
    final Expression[] children;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class Tuple implements Expression {

    final TupleKeyValue[] children;
    Ty ty;

    @Override
    public String toString() {
      return STR."(\{String.join(", ", Arrays.stream(children).map(TupleKeyValue::toString).toList())})";
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class TupleKeyValue implements Expression {

    final Identifier key;
    @Nonnull
    final Hir.Expression value;
    Ty ty;
  }

  @Data
  @AllArgsConstructor
  public static class TyExpr implements Expression {

    Ty ty;

    @Override
    public String toString() {
      return ty.toShortString();
    }
  }

  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class VariableDeclaration implements Expression {

    final Identifier identifier;
    final MutabilityKind mutabilityKind;
    final Expression type;
    Ty ty;

    @Override
    public String toString() {
      return STR."\{mutabilityKind} \{identifier}:\{type}";
    }
  }
}
