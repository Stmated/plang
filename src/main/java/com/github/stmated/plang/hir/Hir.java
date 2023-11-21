package com.github.stmated.plang.hir;

import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyValue;
import java.util.Arrays;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;
import jakarta.validation.constraints.NotNull;

@UtilityClass
public class Hir {

  @Data
  @AllArgsConstructor
  public static class Argument implements Expression {

    String label;
    @NotNull
    Hir.Expression value;
  }

  @Data
  @AllArgsConstructor
  public static class Array implements Expression {

    Expression[] elements;
    Expression elementType;
    Expression length;

    @Override
    public String toString() {

      final var childrenStrings = Arrays.stream(elements()).map(Object::toString).toList();
      final var childrenString = String.join(", ", childrenStrings);

      return STR."[\{childrenString};\{elementType()};\{length()}]";
    }
  }

  @Data
  @AllArgsConstructor
  public static class ArrayAccess implements Expression {

    Expression target;
    Expression accessor;

    @Override
    public String toString() {
      return STR."\{target}[\{accessor}]";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Assignment implements Expression {

    Expression lhs;
    Expression rhs;

    @Override
    public String toString() {
      return STR."\{lhs} = \{rhs}";
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
  public static class Block implements Expression {
    Expression children;
  }

  @Data
  @AllArgsConstructor
  public static class Call implements Expression {

    Expression target;
    Argument[] arguments;
    boolean partial;
  }

  @Data
  @AllArgsConstructor
  public static class Conditional implements Expression {

    Expression predicate;
    Expression pass;
    Expression fail;

    @Override
    public String toString() {
      return STR."if (\{this.predicate()}) then {\{this.pass()}} else {\{this.fail()}}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Expressions implements Expression {

    Expression[] children;

    @Override
    public String toString() {
      final var childStrings = String.join("; ", Arrays.stream(children()).map(Objects::toString).toList());
      return STR."[\{childStrings}]";
    }
  }

  public interface Expression {

  }

  @Data
  @AllArgsConstructor
  public static class Function implements Expression {

    FunctionSignature signature;
    Expression body;

    @Override
    public String toString() {
      return STR."\{signature} => \{body}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class FunctionSignature implements Expression {

    @NotNull
    Parameter[] parameters;
    boolean vararg;
    Expression returnType;

    @Override
    public String toString() {

      final var parameterStrings = Arrays.stream(parameters()).map(Parameter::toString).toList();
      return STR."(\{String.join(", ", parameterStrings)}\{vararg() ? "..." : ""}): \{returnType}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Identifier implements Expression {

    @NotNull
    String name;

    @Override
    public String toString() {
      return name;
    }
  }

  @Data
  @AllArgsConstructor
  public static class Labeling implements Expression {

    @NotNull
    Hir.Expression lhs;
    @NotNull
    Hir.Expression rhs;
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
  public static class Loop implements Expression {

    Expression body;
  }

  /**
   * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
   *        Are there benefits to being able to represent a "break" further down the chain?
   */
  @Data
  @AllArgsConstructor
  public static class LoopBreak implements Expression {

    Expression value;

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
  public static class NewByBlock implements Expression {

    Expression target;
    Identifier allocator;
    Assignment[] fields;

    @Override
    public String toString() {
      return STR."new \{target}{\{Arrays.toString(fields)}}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class NewByCtor implements Expression {

    Expression target;
    Identifier allocator;
    Expression arguments;

    @Override
    public String toString() {
      return STR."new \{target}(\{arguments})";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Not implements Expression {

    Expression expression;
  }

  @Data
  @AllArgsConstructor
  public static class Parameter implements Expression {

    @NotNull
    Hir.Expression identifier;
    @NotNull
    Hir.Expression type;
    boolean vararg;

    @Override
    public String toString() {
      return STR."\{identifier}:\{type}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Path implements Expression {

    Expression[] elements;

    @Override
    public String toString() {
      return Arrays.toString(elements);
    }
  }

  @Data
  @AllArgsConstructor
  public static class Program implements Expression {
    Expression expressions;
  }

  @Data
  @AllArgsConstructor
  public static class Range implements Expression {
    Expression lower;
    Expression higher;
  }

  @Data
  @AllArgsConstructor
  public static class Return implements Expression {

    Expression expression;

    @Override
    public String toString() {
      return STR."return \{expression}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Struct implements Expression {

    VariableDeclaration[] declarations;

    @Override
    public String toString() {
      return STR."struct {\{Arrays.toString(declarations)}}";
    }
  }

  @Data
  @AllArgsConstructor
  public static class Trait implements Expression {
    Expression[] children;
  }

  @Data
  @AllArgsConstructor
  public static class Tuple implements Expression {

    TupleKeyValue[] children;

    @Override
    public String toString() {
      return STR."(\{String.join(", ", Arrays.stream(children).map(TupleKeyValue::toString).toList())})";
    }
  }

  @Data
  @AllArgsConstructor
  public static class TupleKeyValue implements Expression {

    Identifier key;
    @NotNull
    Hir.Expression value;
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
  public static class VariableDeclaration implements Expression {

    Identifier identifier;
    MutabilityKind mutabilityKind;
    Expression type;

    @Override
    public String toString() {
      return STR."\{mutabilityKind} \{identifier}:\{type}";
    }
  }
}
