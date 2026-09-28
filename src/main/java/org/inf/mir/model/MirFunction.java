package org.inf.mir.model;

import org.inf.mir.Mir;
import org.inf.ty.Ty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MirFunction {

  private final String name;
  private final MirFnSignature signature;
  private final List<MirNode> blocks = new ArrayList<>();
  private final List<Mir.Local> locals = new ArrayList<>();
  private int nextValue;

  public MirFunction(String name, MirFnSignature signature, boolean external) {
    this.name = Objects.requireNonNull(name);
    this.signature = Objects.requireNonNull(signature);
    if (!external) {
      newBlock("entry");
    }
  }

  public String name() {
    return name;
  }

  public MirFnSignature signature() {
    return signature;
  }

  public boolean external() {
    return blocks.isEmpty();
  }

  public MirNode entry() {
    if (external()) {
      throw new IllegalStateException("External function has no entry block: " + name);
    }
    return blocks.getFirst();
  }

  public List<MirNode> blocks() {
    return Collections.unmodifiableList(blocks);
  }

  public List<Mir.Local> locals() {
    return Collections.unmodifiableList(locals);
  }

  public MirNode newBlock(String label) {
    final var block = new MirNode(label + "_" + blocks.size());
    blocks.add(block);
    return block;
  }

  public Mir.Local newLocal(String label, Ty type) {
    final var local = new Mir.Local(locals.size(), label, type);
    locals.add(local);
    return local;
  }

  public Mir.Value newValue(Ty type) {
    return new Mir.Value(nextValue++, type);
  }
}
