package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.util.Box;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.commons.nullanalysis.NotNull;

public record MirNode(
  String name,
  @NotNull
  List<MirInstr> instructions,
  @NotNull
  List<MirNode> predecessors,
  @NotNull
  List<MirNode> successors,
  @NotNull
  Box<Ty> ty
) {

  public MirNode(String name) {
    this(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new Box<>());
  }

//  public Ty ty() {
//    return this.instructions().getLast().ty();
//  }
//
//  public Ty resultingTy() {
//    return this.instructions().getLast().resultingTy();
//  }

  public boolean isTerminal() {

    if (this.instructions().isEmpty()) {
      return false;
    }

    return this.instructions().getLast().isTerminal();
  }

  public void addSuccessor(MirNode successor) {
    successors().add(successor);
    successor.predecessors().add(this);
  }

  @Override
  public String toString() {
    final var from = String.join(", ", predecessors().stream().map(MirNode::name).toList());
    final var to = String.join(", ", successors().stream().map(MirNode::name).toList());

    final var terminalStr = isTerminal()
      ? "*"
      : "";

    final var singleInstruction = instructions.size() == 1
      ? STR." does \{instructions.getFirst()}"
      : "";

    return STR."\{terminalStr}\{name()} (from [\{from}] to [\{to}])\{singleInstruction}";
  }

  public String toShortString() {
    return name();
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }
}
