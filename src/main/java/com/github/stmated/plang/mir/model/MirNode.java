package com.github.stmated.plang.mir.model;

import java.util.ArrayList;
import java.util.List;

public record MirNode(
  String name,
  List<MirInstr> instructions,
  List<MirNode> predecessors,
  List<MirNode> successors
) {

  public MirNode(String name) {
    this(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

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
