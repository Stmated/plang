package org.inf.hir;

import java.util.Objects;

/// Shared argument placement for type checking and lowering; evaluation remains in source order.
/// TODO This is not the greatest design, it feels a bit like a hack. Consider refactoring/rethiking.
public final class HirArgumentBinding {

  private final String[] parameters;
  private final boolean vararg;
  private final boolean[] bound;
  private int positional;

  public HirArgumentBinding(String[] parameters, boolean vararg, int argumentCount) {
    this.parameters = parameters;
    this.vararg = vararg;
    this.bound = new boolean[Math.max(parameters.length, argumentCount)];
  }

  public int bind(String label) {
    int index;
    if (label != null) {
      index = -1;
      for (var i = 0; i < parameters.length; i++) {
        if (Objects.equals(label, parameters[i])) {
          index = i;
          break;
        }
      }
      if (index < 0) {
        throw new IllegalArgumentException("Unknown argument label: " + label);
      }
    } else {
      while (positional < bound.length && bound[positional]) {
        positional++;
      }
      index = positional++;
    }
    if (index >= bound.length || bound[index]) {
      throw new IllegalArgumentException("Duplicate or excess argument");
    }
    if (index >= parameters.length && !vararg) {
      throw new IllegalArgumentException("Too many arguments for function");
    }
    bound[index] = true;
    return index;
  }

  public void requireComplete() {
    for (final var argument : bound) {
      if (!argument) {
        throw new IllegalArgumentException("Missing function argument");
      }
    }
  }
}
