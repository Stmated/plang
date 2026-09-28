package org.inf.mir;

import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;

import java.util.List;
import java.util.Objects;

public record MirLoweringResult(MirFunction script, List<MirFunction> functions) {

  public MirLoweringResult {
    Objects.requireNonNull(script);
    functions = List.copyOf(functions);
    if (!functions.contains(script)) {
      throw new IllegalArgumentException("Module must own its script function");
    }
  }

  public MirNode initNode() {
    return script.entry();
  }
}
