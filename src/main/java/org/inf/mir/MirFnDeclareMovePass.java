package org.inf.mir;

import org.inf.mir.model.MirNode;
import java.util.ArrayList;
import java.util.List;

public class MirFnDeclareMovePass {

  public static void startPass(MirNode node) {

    // TODO: Move all function declarations to top-level of the MIR?
    final var pass = new MirFnDeclareMovePass();
    pass.enter(node);
  }

  private final List<MirNode> visitedNodes = new ArrayList<>();

  private void enter(MirNode node) {

    if (visitedNodes.contains(node)) {
      return;
    }

    visitedNodes.add(node);

    for (final var successor : node.successors()) {
      enter(successor);
    }
  }
}
