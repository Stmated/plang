package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirBinaryOperationKind;
import com.github.stmated.plang.mir.model.MirFnArgument;
import com.github.stmated.plang.mir.model.MirFnParameter;
import com.github.stmated.plang.mir.model.MirFnSignature;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyStruct;
import com.github.stmated.plang.ty.TyValueArray;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.UtilityClass;
//import org.codehaus.commons.nullanalysis.NotNull;
//import org.codehaus.commons.nullanalysis.Nullable;

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
      return STR."\{lhs.toShortString()} \{kind} \{rhs.toShortString()}";
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
      return STR."if \{predicate} then \{pass.name()} else \{fail.name()}";
    }

    @Override
    public Ty ty() {
      return Ty.VOID;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  public static class InstrCreateFn extends AbstractInstr {

    MirNode entry;
    @NotNull
    MirFnSignature signature;
    @NotNull
    Ty ty;

    @Override
    public String toString() {
      return STR."\{name() == null ? "anon" : name()}\{signature} @ \{entry}";
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
      return STR."\{content}:\{ty.toShortString()}";
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
      return STR."param:\{parameter.name()}";
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
      return STR."Jump To '\{node.name()}'";
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
        strings[i] = STR."\{operands[i].toShortString()} from \{from[i].toShortString()}";
      }

      return STR."Φ \{String.join(" OR ", strings)}";
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
      return STR."new \{allocator} \{ty}(\{Arrays.toString(arguments)})";
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

      return STR."[\{elementsString};\{ty().toShortString()};\{length()}]";
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
      return STR."\{target()}[\{accessor}]";
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
      return STR."\{target()}[\{index}]";
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
  public static class InstrStore extends AbstractInstr {

    InstrStore target;
    Instr value;
    Ty ty;

    @Override
    public String toString() {
      return STR."Store (\{value.toShortString()})";
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
      return STR."\{target()}[\{index}] = \{value}";
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
      return STR."return \{instr.toShortString()}";
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
