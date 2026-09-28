package org.inf.mir;

import org.inf.mir.model.MirBinaryOperationKind;
import org.inf.mir.model.MirFnSignature;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.Ty;
import org.inf.ty.TyPointer;

import java.util.List;
import java.util.Objects;

/** Values, storage locations, instructions and control transfers are deliberately separate. */
public final class Mir {

  private Mir() {
  }

  public sealed interface Operand permits Value, Constant, FunctionRef, Unit {
    Ty ty();
  }

  public record Value(int id, Ty ty) implements Operand {
    public Value {
      Objects.requireNonNull(ty);
    }
  }

  public record Constant(String content, Ty ty) implements Operand {
    public Constant {
      Objects.requireNonNull(content);
      Objects.requireNonNull(ty);
    }
  }

  public record FunctionRef(MirFunction function) implements Operand {
    public FunctionRef {
      Objects.requireNonNull(function);
    }

    @Override
    public Ty ty() {
      return new TyPointer<>(MirTypes.functionType(function.signature()));
    }
  }

  public enum Unit implements Operand {
    INSTANCE;

    @Override
    public Ty ty() {
      return Ty.VOID;
    }
  }

  public sealed interface Place permits Local, Field, Element {
    Ty ty();

    default List<Operand> operands() {
      return List.of();
    }
  }

  public record Local(int id, String name, Ty ty) implements Place {
    public Local {
      Objects.requireNonNull(name);
      Objects.requireNonNull(ty);
    }
  }

  public record Field(Operand target, int index, Ty ty) implements Place {
    public Field {
      Objects.requireNonNull(target);
      Objects.requireNonNull(ty);
    }

    @Override
    public List<Operand> operands() {
      return List.of(target);
    }
  }

  public record Element(Operand target, Operand index, Ty ty) implements Place {
    public Element {
      Objects.requireNonNull(target);
      Objects.requireNonNull(index);
      Objects.requireNonNull(ty);
    }

    @Override
    public List<Operand> operands() {
      return List.of(target, index);
    }
  }

  public sealed interface Instruction permits Binary, Load, Store, Call, Convert, UnionVariant, NewArray, NewStruct, Parameter {
    default Value result() {
      return null;
    }

    List<Operand> operands();
  }

  public record Binary(Value result, Operand lhs, MirBinaryOperationKind kind, Operand rhs) implements Instruction {
    @Override
    public List<Operand> operands() {
      return List.of(lhs, rhs);
    }
  }

  public record Load(Value result, Place place) implements Instruction {
    @Override
    public List<Operand> operands() {
      return place.operands();
    }
  }

  public record Store(Place place, Operand value) implements Instruction {
    @Override
    public List<Operand> operands() {
      final var operands = new java.util.ArrayList<>(place.operands());
      operands.add(value);
      return List.copyOf(operands);
    }
  }

  /** A void call has no result value. Argument positions have already been resolved. */
  public record Call(Value result, Operand target, MirFnSignature signature, List<Operand> arguments) implements Instruction {
    public Call {
      arguments = List.copyOf(arguments);
    }

    @Override
    public List<Operand> operands() {
      final var operands = new java.util.ArrayList<Operand>();
      operands.add(target);
      operands.addAll(arguments);
      return List.copyOf(operands);
    }
  }

  /** Explicit numeric/pointer conversion, or widening from one tagged union to another. */
  public record Convert(Value result, Operand value) implements Instruction {
    @Override
    public List<Operand> operands() {
      return List.of(value);
    }
  }

  public record UnionVariant(Value result, int variant, Operand value) implements Instruction {
    @Override
    public List<Operand> operands() {
      return List.of(value);
    }
  }

  /** Elements are evaluated once; their values repeat when length exceeds the initializer count. */
  public record NewArray(Value result, List<Operand> elements, Operand length) implements Instruction {
    public NewArray {
      elements = List.copyOf(elements);
    }

    @Override
    public List<Operand> operands() {
      final var operands = new java.util.ArrayList<>(elements);
      operands.add(length);
      return List.copyOf(operands);
    }
  }

  /** Field values are in layout order, after evaluation in source order. */
  public record NewStruct(Value result, List<Operand> fields) implements Instruction {
    public NewStruct {
      fields = List.copyOf(fields);
    }

    @Override
    public List<Operand> operands() {
      return fields;
    }
  }

  public record Parameter(Value result, int index) implements Instruction {
    @Override
    public List<Operand> operands() {
      return List.of();
    }
  }

  public sealed interface Terminator permits Jump, Branch, Return, Unreachable {
    default List<MirNode> successors() {
      return List.of();
    }

    default List<Operand> operands() {
      return List.of();
    }
  }

  public record Jump(MirNode target) implements Terminator {
    public Jump {
      Objects.requireNonNull(target);
    }

    @Override
    public List<MirNode> successors() {
      return List.of(target);
    }
  }

  public record Branch(Operand predicate, MirNode pass, MirNode fail) implements Terminator {
    public Branch {
      Objects.requireNonNull(predicate);
      Objects.requireNonNull(pass);
      Objects.requireNonNull(fail);
    }

    @Override
    public List<MirNode> successors() {
      return pass == fail ? List.of(pass) : List.of(pass, fail);
    }

    @Override
    public List<Operand> operands() {
      return List.of(predicate);
    }
  }

  public record Return(Operand value) implements Terminator {
    public Return {
      Objects.requireNonNull(value);
    }

    @Override
    public List<Operand> operands() {
      return List.of(value);
    }
  }

  public record Unreachable() implements Terminator {
  }
}
