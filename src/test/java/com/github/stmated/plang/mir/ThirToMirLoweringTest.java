package com.github.stmated.plang.mir;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.mir.model.MirInstrBinaryOperation;
import com.github.stmated.plang.mir.model.MirInstrConditionalJump;
import com.github.stmated.plang.mir.model.MirInstrCreateLiteral;
import com.github.stmated.plang.mir.model.MirInstrJump;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.mir.model.MirInstrPhi;
import com.github.stmated.plang.mir.model.MirReturn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ThirToMirLoweringTest {

  @Test
  void testBinaryOperation() {

    final var mir = Plang.codeToMir("1 + 1");

    Assertions.assertEquals(0, mir.predecessors().size());
    Assertions.assertEquals(0, mir.successors().size());
    Assertions.assertEquals(4, mir.instructions().size());
  }

  @Test
  void testDeclarationAndReassignment() {

    final var mir = Plang.codeToMir("var a = 0; a = a + 1; return a");

    Assertions.assertEquals(0, mir.predecessors().size());
    Assertions.assertEquals(0, mir.successors().size());
    Assertions.assertEquals(4, mir.instructions().size());

    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(0));
    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(1));
    Assertions.assertInstanceOf(MirInstrBinaryOperation.class, mir.instructions().get(2));
    Assertions.assertInstanceOf(MirReturn.class, mir.instructions().get(3));
  }

  @Test
  void testAssignments() {

    final var mir = Plang.codeToMir("var a = 0; var b = a + 1; return b;");

    Assertions.assertEquals(0, mir.predecessors().size());
    Assertions.assertEquals(0, mir.successors().size());
    Assertions.assertEquals(4, mir.instructions().size());

    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(0));
    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(1));
    Assertions.assertInstanceOf(MirInstrBinaryOperation.class, mir.instructions().get(2));
    Assertions.assertInstanceOf(MirReturn.class, mir.instructions().get(3));
  }

  @Test
  void testConditional() {

    final var mir = Plang.codeToMir("var a = 0; if (a == 0) { a = 10; } else { a = 20; } return a;");

    Assertions.assertEquals(4, mir.instructions().size());

    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(0));
    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(1));
    Assertions.assertInstanceOf(MirInstrBinaryOperation.class, mir.instructions().get(2));
    Assertions.assertInstanceOf(MirInstrConditionalJump.class, mir.instructions().get(3));

    assertEdges(mir, new Edge[]{
      new Edge("start", "conditional_pass"),
      new Edge("start", "conditional_fail"),
      new Edge("conditional_pass", "conditional_merge"),
      new Edge("conditional_fail", "conditional_merge")
    });

    final var conditional_pass = mir.successors().getFirst();
    Assertions.assertEquals("conditional_pass", conditional_pass.name());
    Assertions.assertEquals(2, conditional_pass.instructions().size());
    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, conditional_pass.instructions().getFirst());
    Assertions.assertInstanceOf(MirInstrJump.class, conditional_pass.instructions().getLast());

    final var conditional_fail = mir.successors().getLast();
    Assertions.assertEquals("conditional_fail", conditional_fail.name());
    Assertions.assertEquals(2, conditional_fail.instructions().size());
    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, conditional_fail.instructions().getFirst());
    Assertions.assertInstanceOf(MirInstrJump.class, conditional_fail.instructions().getLast());

    final var conditional_merge = conditional_fail.successors().getFirst();
    Assertions.assertEquals("conditional_merge", conditional_merge.name());
    Assertions.assertEquals(2, conditional_merge.instructions().size());
    Assertions.assertInstanceOf(MirInstrPhi.class, conditional_merge.instructions().getFirst());
    Assertions.assertInstanceOf(MirReturn.class, conditional_merge.instructions().getLast());
  }

  @Test
  void testLoopWithOutsideVariable() {

    // TODO: Need a way to easily test and verify a MIR structure
    //        Maybe some easy string format that lets us "pattern match" the structure
    //        Such as saying: "A -> B, B -> C, B -> D, D -> E" to give the node/edge path of the whole CFG

    // TODO: There also needs to be some way of describing the structure, but that seems almost impossible?

    final var mir = Plang.codeToMir("var a = 0; for (var i = 0; i < 10; i += 1) { a += i } return a;");

    Assertions.assertEquals(0, mir.predecessors().size());
    Assertions.assertEquals(1, mir.successors().size());
    Assertions.assertEquals(3, mir.instructions().size());

    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(0));
    Assertions.assertInstanceOf(MirInstrCreateLiteral.class, mir.instructions().get(1));
    Assertions.assertInstanceOf(MirInstrJump.class, mir.instructions().get(2));

    assertEdges(mir, new Edge[]{
      new Edge("start", "loop"),
      new Edge("loop", "conditional_pass"),
      new Edge("loop", "conditional_fail"),
      new Edge("conditional_pass", "loop"),
      new Edge("conditional_fail", "loop_after")
    });
  }

  private record Edge(String from, String to) {

    @Override
    public String toString() {
      return STR."\{from} -> \{to}";
    }
  }

  private void assertEdges(MirNode start, Edge[] edges) {

    final var found = new HashSet<Edge>();
    final var expected = new HashSet<>(List.of(edges));
    final var remaining = new HashSet<>(expected);

    final var visited = new ArrayList<MirNode>();
    final var nodes = new Stack<MirNode>();
    nodes.push(start);

    while (!nodes.isEmpty()) {

      final var node = nodes.pop();

      for (final var successor : node.successors()) {

        final var localEdge = new Edge(node.name(), successor.name());

        if (!expected.contains(localEdge)) {
          Assertions.fail(STR."Encountered unexpected edge: \{localEdge}");
        }

        remaining.remove(localEdge);
        found.add(localEdge);

        if (visited.contains(successor)) {
          continue;
        }

        visited.add(successor);
        nodes.push(successor);
      }
    }

    if (!remaining.isEmpty()) {
      Assertions.fail(STR."Edges not found:\n\{remaining}\nBut found: \{found}");
    }
  }
}
