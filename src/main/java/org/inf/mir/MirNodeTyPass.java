package org.inf.mir;

import org.inf.mir.Mir.InstrConditionalJump;
import org.inf.mir.Mir.InstrJump;
import org.inf.mir.model.MirNode;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;
import java.util.ArrayList;
import java.util.List;

public class MirNodeTyPass {

  public static void startPass(MirNode node) {

    final var pass = new MirNodeTyPass();
    pass.enter(node);
  }

  private final List<MirNode> visitedNodes = new ArrayList<>();

  private Ty enter(MirNode node) {

    if (visitedNodes.contains(node)) {
      return Ty.DEADEND;
    }

    visitedNodes.add(node);

    if (node.ty() != null) {
      return node.ty();
    }

    final var lastInstruction = node.instructions().getLast();
    if (lastInstruction != null) {

      final var ty = switch (lastInstruction) {
        case InstrJump jump -> enter(jump.node());
        case InstrConditionalJump jump -> {
          final var a = enter(jump.pass());
          final var b = enter(jump.fail());

          if (a == Ty.DEADEND && b == Ty.DEADEND) {
            yield Ty.INVALID;
          } else if (a == Ty.DEADEND) {
            yield b;
          } else if (b == Ty.DEADEND) {
            yield a;
          }

          yield Tys.union(a, b);
        }
        default -> lastInstruction.ty();
      };

      return node.ty(ty).ty();

    } else {
      return Ty.INVALID;
    }
  }
}
