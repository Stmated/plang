package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirFnSignature;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.thir.raising.ThirRaiseResult;
import java.util.ArrayDeque;
import java.util.Deque;
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

    throw new IllegalArgumentException(STR."No such instruction '\{name}' found");
  }

  public record LoopHandle(MirNode next, MirNode exit) {

  }

  ThirToMirCtx parent;
  Deque<LoopHandle> loopStack = new ArrayDeque<>();
  Deque<MirNode> nodeStack = new ArrayDeque<>();
  Deque<MirScope> scopeStack = new ArrayDeque<>();
  Deque<MirFnSignature> fnStack = new ArrayDeque<>();

  ThirRaiseResult thirRaiseResult;
}
