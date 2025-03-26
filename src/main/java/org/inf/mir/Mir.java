package org.inf.mir;

import org.inf.mir.model.MirBinaryOperationKind;
import org.inf.mir.model.MirFnArgument;
import org.inf.mir.model.MirFnParameter;
import org.inf.mir.model.MirFnSignature;
import org.inf.mir.model.MirNode;
import org.inf.ty.Ty;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Mir {

  public interface Instr {

    MirIdentifierId name();

    default boolean isTerminal() {
      return false;
    }

    default String toShortString() {
      return this.toString();
    }

    /**
     * The intrinsic result ty of the instruction itself.
     */
    Ty ty();
  }

  @Data
  public abstract static class AbstractInstr implements Instr {

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private MirIdentifierId name;

    @Override
    public MirIdentifierId name() {
      return name;
    }

    public void name(MirIdentifierId iid) {
      this.name = iid;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }
  }

  /**
   * TODO: This should be a terminal instruction which has a "success" and a "fail" path (for GC and other unwind)
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrCall extends AbstractInstr {

    Instr target;
    MirFnSignature fnSignature;
    MirFnArgument[] arguments;

    @Override
    public Ty ty() {
      return fnSignature.returnType();
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrBinaryOperation extends AbstractInstr {

    Instr lhs;
    MirBinaryOperationKind kind;
    Instr rhs;
    Ty ty;

    @Override
    public String toString() {
      return lhs.toShortString() + " " + kind + " " + rhs.toShortString();
    }

    public Instr lhs() {
      return lhs;
    }

    public MirBinaryOperationKind kind() {
      return kind;
    }

    public Instr rhs() {
      return rhs;
    }

    @Override
    public Ty ty() {
      return ty;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrConditionalJump extends AbstractInstr {

    Instr predicate;
    MirNode pass;
    MirNode fail;

    @Override
    public String toString() {
      return "if " + predicate + " then " + pass.name() + " else " + fail.name();
    }

    @Override
    public Ty ty() {
      return Ty.VOID;
    }
  }

  @Data

  @EqualsAndHashCode(callSuper = true)
  @AllArgsConstructor
  public static class InstrCreateFn extends AbstractInstr {

    MirNode entry;
    @Nonnull
    MirFnSignature signature;
    @Nonnull
    Ty ty;

    @Override
    public String toString() {
      return (name() == null ? "anon" : name()) + "" + signature + " @ " + entry;
    }

    @Override
    public Ty ty() {
      return ty;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrCreateLiteral extends AbstractInstr {

    String content;
    Ty ty;

    @Override
    public String toString() {
      return content + ":" + ty.toShortString();
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrCreateStruct extends AbstractInstr {

    TyStruct ty;

    @Override
    public String toString() {
      return Objects.toString(ty);
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrGetParam extends AbstractInstr {

    MirFnParameter parameter;

    @Override
    public Ty ty() {
      return parameter.ty();
    }

    @Override
    public String toString() {
      return "param:" + parameter.name();
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrJump extends AbstractInstr {

    MirNode node;

    @Override
    public boolean isTerminal() {
      return true;
    }

    @Override
    public String toString() {
      return "Jump To '" + node.name() + "'";
    }

    @Override
    public Ty ty() {
      return Ty.VOID;
    }
  }

  /**
   * The phi operand of the result of a branching. That is the result of the final instruction of the pass or fail nodes. The result could be
   * nothing.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrPhi extends AbstractInstr {

    Instr[] operands;
    MirNode[] from;
    Ty ty;

    @Override
    public String toString() {

      final var strings = new String[operands.length];
      for (var i = 0; i < strings.length; i++) {
        strings[i] = "%s from %s".formatted(operands[i].toShortString(), from[i].toShortString());
      }

      return "Φ %s".formatted(String.join(" OR ", strings));
    }

    @Override
    public Ty ty() {
      return ty;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrGetGlobal extends AbstractInstr {

    String globalName;
    Ty ty;

    @Override
    public String toString() {
      return globalName;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrCreateInstance extends AbstractInstr {

    Ty ty;

    Instr allocator;

    /**
     * The arguments will be in the order of the fields of the type that we are creating an instance of. Either all fields must be present, or all fields of the
     * constructor must be.
     */
    Instr[] arguments;

    @Override
    public String toString() {
      return "new %s %s(%s)".formatted(allocator, ty, Arrays.toString(arguments));
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrCreateArray extends AbstractInstr {

    Instr[] elements;
    Instr length;
    TyValueArray ty;
    Ty elementTy;

    @Override
    public String toString() {

      final var elementStrings = Arrays.stream(elements()).map(Object::toString).toList();
      final var elementsString = String.join(", ", elementStrings);

      return "[%s;%s;%s]".formatted(elementsString, ty().toShortString(), length());
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrGetArrayElement extends AbstractInstr {

    Instr target;
    Instr accessor;
    Ty ty;

    @Override
    public String toString() {
      return target() + "[" + accessor + "]";
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrGetStructElement extends AbstractInstr {

    Instr target;
    int index;
    Ty ty;

    @Override
    public String toString() {
      return target() + "[" + index + "]";
    }
  }

  /**
   * Load the value of a previous store.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrLoad extends AbstractInstr {

    InstrStore store;

    @Override
    public Ty ty() {
      return store.ty();
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrReference extends AbstractInstr {

    Instr target;

    @Override
    public Ty ty() {
      return target.ty();
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrStore extends AbstractInstr {

    InstrStore target;
    Instr value;
    Ty ty;

    @Override
    public String toString() {
      return "Store (" + value.toShortString() + ")";
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrSetStructElement extends AbstractInstr {

    Instr target;
    int index;
    Instr value;
    Ty ty;

    @Override
    public String toString() {
      return target() + "[" + index + "] = " + value;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrReturn extends AbstractInstr {

    Instr instr;

    public InstrReturn(Instr instr) {
      this.instr = Objects.requireNonNull(instr, "Return operand not allowed to be null");
    }

    @Override
    public boolean isTerminal() {
      return true;
    }

    @Override
    public String toString() {
      return "return " + instr.toShortString();
    }

    @Override
    public Ty ty() {
      return instr.ty();
    }

    public Instr instr() {
      return instr;
    }
  }
}
