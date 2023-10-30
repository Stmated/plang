package com.github.stmated.plang.mir.model;

public record MirInstrJump(MirNode node) implements MirInstruction {

  @Override
  public boolean isTerminal() {
    return true;
  }

  @Override
  public String toString() {
    return STR."Jump To '\{node.name()}'";
  }
}
