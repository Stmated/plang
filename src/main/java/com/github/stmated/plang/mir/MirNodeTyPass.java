package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirInstrConditionalJump;
import com.github.stmated.plang.mir.model.MirInstrJump;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.util.Tys;
import java.util.ArrayList;
import java.util.List;

public class MirNodeTyPass {

  public static void startNodeTyPass(MirNode node) {

    final var pass = new MirNodeTyPass();
    pass.enter(node);
  }

  private final List<MirNode> visitedNodes = new ArrayList<>();

  private Ty enter(MirNode node) {

    if (visitedNodes.contains(node)) {
      return Ty.DEADEND;
    }

    visitedNodes.add(node);

    if (node.ty().value() != null) {
      return node.ty().value();
    }

    final var lastInstruction = node.instructions().getLast();
    if (lastInstruction != null) {

      final var ty = switch (lastInstruction) {
        case MirInstrJump jump -> enter(jump.node());
        case MirInstrConditionalJump jump -> {
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

      node.ty().value(ty);
      return ty;

    } else {
      return Ty.INVALID;
    }
  }
}
