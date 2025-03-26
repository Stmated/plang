package org.inf.mir;

import org.inf.mir.model.MirFnSignature;
import org.inf.mir.model.MirNode;
import org.inf.thir.raising.ThirRaiseResult;
import org.inf.ty.util.MachineTarget;
import java.util.ArrayDeque;
import java.util.Deque;
import lombok.Getter;
import lombok.Value;

@Value
public class ThirToMirCtx {

  public Mir.Instr getInstructionByName(String name) {

    final var instr = this.scopeStack().peek().get(name);
    if (instr != null) {
      return instr;
    }

    if (this.parent != null) {
      return this.parent.getInstructionByName(name);
    }

    throw new IllegalArgumentException("No such instruction '" + name + "' found");
  }

  public record LoopHandle(MirNode next, MirNode exit) {

  }

  ThirToMirCtx parent;
  @Getter
  MachineTarget machineTarget;
  Deque<LoopHandle> loopStack = new ArrayDeque<>();
  Deque<MirNode> nodeStack = new ArrayDeque<>();
  Deque<MirScope> scopeStack = new ArrayDeque<>();
  Deque<MirFnSignature> fnStack = new ArrayDeque<>();

  ThirRaiseResult thirRaiseResult;
}
