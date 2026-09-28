package org.inf.mir.model;

import org.inf.mir.Mir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A basic block; identity is independent of its diagnostic name. */
public final class MirNode {

  private final String name;
  private final List<Mir.Instruction> instructions = new ArrayList<>();
  private Mir.Terminator terminator;

  public MirNode(String name) {
    this.name = Objects.requireNonNull(name);
  }

  public String name() {
    return name;
  }

  public List<Mir.Instruction> instructions() {
    return Collections.unmodifiableList(instructions);
  }

  public Mir.Terminator terminator() {
    return terminator;
  }

  public List<MirNode> successors() {
    return terminator == null ? List.of() : terminator.successors();
  }

  public void append(Mir.Instruction instruction) {
    requireOpen();
    instructions.add(Objects.requireNonNull(instruction));
  }

  public void terminate(Mir.Terminator terminal) {
    requireOpen();
    terminator = Objects.requireNonNull(terminal);
  }

  private void requireOpen() {
    if (terminator != null) {
      throw new IllegalStateException("Block '" + name + "' already has a terminator");
    }
  }

  @Override
  public String toString() {
    return name;
  }
}
