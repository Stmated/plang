package com.github.stmated.plang.mir;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.model.HirArgument;
import com.github.stmated.plang.hir.model.HirAssignment;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBlock;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirConditional;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirExpressionCollection;
import com.github.stmated.plang.hir.model.HirIdentifier;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirLoop;
import com.github.stmated.plang.hir.model.HirLoopBreak;
import com.github.stmated.plang.hir.model.HirLoopContinue;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.hir.model.HirVariableDeclaration;
import com.github.stmated.plang.mir.model.MirBinaryOperationKind;
import com.github.stmated.plang.mir.model.MirAssignment;
import com.github.stmated.plang.mir.model.MirBinaryOperation;
import com.github.stmated.plang.mir.model.MirCall;
import com.github.stmated.plang.mir.model.MirInstrConditionalJump;
import com.github.stmated.plang.mir.model.MirInstrJump;
import com.github.stmated.plang.mir.model.MirOperandPhi;
import com.github.stmated.plang.mir.model.MirOperandNodeResult;
import com.github.stmated.plang.mir.model.MirReturn;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.mir.model.MirOperand;
import com.github.stmated.plang.mir.model.MirOperandHirExpression;
import java.util.HashMap;
import java.util.Stack;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * NOTE: There will be quite some references from the MIR to the HIR, for sake of faster development compiler-side. It is however a goal to try and specialize
 * the MIR as much as possible and separate it away from the HIR.
 */
@Slf4j
public class HirToMirLowering {

  private record LoopScope(MirNode next, MirNode exit) {

  }

  private final Stack<LoopScope> loopStack = new Stack<>();

  private final Stack<MirNode> nodeStack = new Stack<>();
  private final Stack<MirScope> scopeStack = new Stack<>();

  public MirNode lower_program(HirProgram hir) {

    final var startNode = new MirNode("start");

    try {
      scopeStack.push(new MirScope(null, "global"));
      try {
        nodeStack.push(startNode);
        lower_expressions(hir.expressions());
      } finally {
        nodeStack.pop();
      }
    } finally {
      scopeStack.pop();
    }

    return startNode;
  }

  private MirOperand lower_expressions(HirExpression[] expressions) {

    MirOperand lastOperand = null;
    for (final var expression : expressions) {
      final var operand = lower_expression(expression);
      if (operand != null) {
//        if (lastOperand != null) {
//          throw new IllegalStateException("You must handle expression collections that could result in multiple operands higher in call chain");
//        }

        lastOperand = operand;
      }
    }

    return lastOperand;
  }

  private MirOperand lower_expression(HirExpression expr) {

    return switch (expr) {
      case HirLiteral hir -> lower_literal(hir);
      case HirReturn hir -> lower_return(hir);
      case HirBinaryOperation hir -> lower_binary_operation(hir);
      case HirArgument hir -> lower_argument(hir);
      case HirCall hir -> lower_call(hir);
      case HirConditional hir -> lower_conditional(hir);
      case HirBlock hir -> lower_block(hir);
      case HirVariableDeclaration hir -> lower_variable_declaration(hir);
      case HirAssignment hir -> lower_assignment(hir);
      case HirIdentifier hir -> lower_identifier(hir);
      case HirLoop hir -> lower_loop(hir);
      case HirLoopBreak hir -> lower_loop_break(hir);
      case HirLoopContinue hir -> lower_loop_continue(hir);
      case HirExpressionCollection v -> lower_expressions(v.children());
      case HirProgram v -> lower_expressions(v.expressions());

      default -> throw new NotImplementedException(STR."Do not know how to handle '\{expr}'");
    };
  }

  private MirOperand lower_loop(HirLoop hir) {

    final var node_loop = new MirNode("loop");
    nodeStack.peek().addSuccessor(node_loop);

    final var node_after = new MirNode("loop_after");

    final var jump = new MirInstrJump(node_loop);
    nodeStack.peek().instructions().add(jump);

    try {
      loopStack.push(new LoopScope(node_loop, node_after));
      try {
        nodeStack.push(node_loop);
        return lower_expression(hir.body());
      } finally {
        nodeStack.push(node_loop);

        // We are done with current node, node_after is the future.
        // It is up to later optimization stages to remove if it turns out empty.
        nodeStack.pop();
        nodeStack.push(node_after);
      }
    } finally {
      loopStack.pop();
    }
  }

  private MirOperand lower_loop_continue(HirLoopContinue hir) {

    final var loop = loopStack.peek();
    final var node = nodeStack.peek();

    final var jump = new MirInstrJump(loop.next());
    node.successors().add(loop.next());
    loop.next().predecessors().add(node);
    node.instructions().add(jump);

    return null;
  }

  private MirOperand lower_loop_break(HirLoopBreak hir) {

    final var loop = loopStack.peek();
    final var node = nodeStack.peek();

    final var jump = new MirInstrJump(loop.exit());
    node.successors().add(loop.exit());
    loop.exit().predecessors().add(node);
    node.instructions().add(jump);

    return null;
  }

  private MirOperand lower_identifier(HirIdentifier hir) {

    final var identifierName = hir.name();

    for (var i = scopeStack.size() - 1; i >= 0; i--) {

      final var scope = scopeStack.get(i);
      final var assignment = scope.get(identifierName);
      if (assignment != null) {
        return assignment;
      }
    }

    throw new IllegalArgumentException(STR."There is no variable '\{hir.name()}' found in scope");
  }

  private MirOperand lower_assignment(HirAssignment hir) {

    final var name = switch (hir.lhs()) {
      case HirVariableDeclaration lhs -> lhs.identifier().name();
      case HirIdentifier lhs -> lhs.name();
      default -> throw new UnexpectedExpressionException(hir.lhs());
    };

    final var rhs = lower_expression(hir.rhs());
    final var scope = scopeStack.peek();
    final var iid = scope.newUniqueId(name);

    // TODO: Add the result of the assignment, since it will be easier to follow
    final var assignment = new MirAssignment(iid, rhs);
    scope.add(assignment);

//    final var operand = new MirOperandPhi(assignment);
    nodeStack.peek().instructions().add(assignment);

    return assignment;
  }

  private MirOperand lower_variable_declaration(HirVariableDeclaration hir) {

    log.debug("Variable declaration has no meaning in CFG, handle assignment expressions");
    return null;
  }

  private void addConnection(MirNode source, MirNode destination) {

    source.successors().add(destination);
    destination.predecessors().add(source);
  }

  @Data
  private class PhiEntry {
    MirAssignment a;
    MirNode aNode;
    MirAssignment b;
    MirNode bNode;
  }

  private MirOperand lower_conditional(HirConditional hir) {

    final var predicate_operand = lower_expression(hir.predicate());

    final var scope = scopeStack.peek();

    MirScope pass_scope;
    MirNode pass_node;
    try {
      pass_node = new MirNode("conditional_pass");
      nodeStack.peek().addSuccessor(pass_node);
      nodeStack.push(pass_node);

      pass_scope = new MirScope(scopeStack.peek(), "scope_conditional_pass");
      try {
        scopeStack.push(pass_scope);
        if (hir.pass() != null) {
          final var branch_operand = lower_expression(hir.pass());
        }
      } finally {
        scopeStack.pop();
      }
    } finally {
      nodeStack.pop();
    }

    MirScope fail_scope;
    MirNode fail_node;
    try {

      fail_node = new MirNode("conditional_fail");
      nodeStack.peek().addSuccessor(fail_node);
      nodeStack.push(fail_node);

      fail_scope = new MirScope(scopeStack.peek(), "scope_conditional_fail");
      try {
        scopeStack.push(fail_scope);
        if (hir.fail() != null) {
          final var branch_operand = lower_expression(hir.fail());
        }
      } finally {
        scopeStack.pop();
      }
    } finally {
      nodeStack.pop();
    }

    if (!pass_node.isTerminal() || !fail_node.isTerminal()) {

      final var parent = nodeStack.pop();
      final var node_merge = new MirNode("conditional_merge");
      nodeStack.push(node_merge);

      if (!pass_node.isTerminal()) {

        pass_node.instructions().add(new MirInstrJump(node_merge));
        addConnection(pass_node, node_merge);
      }

      if (!fail_node.isTerminal()) {

        fail_node.instructions().add(new MirInstrJump(node_merge));
        addConnection(fail_node, node_merge);
      }

      final var instruction = new MirInstrConditionalJump(predicate_operand, pass_node, fail_node);
      parent.instructions().add(instruction);

      // TODO: Need to find all overwrites of all variables that are in our current scope, and create phi-nodes from it.

      final var phiMap = new HashMap<String, PhiEntry>();
      for (final var e : pass_scope.map().entrySet()) {
        if (scope.get(e.getKey()) != null) {
          final var phi = phiMap.computeIfAbsent(e.getKey(), _ -> new PhiEntry());
          phi.setA(e.getValue().getLast());
          phi.setANode(pass_node);
        }
      }

      for (final var e : fail_scope.map().entrySet()) {
        if (scope.get(e.getKey()) != null) {
          final var phi = phiMap.computeIfAbsent(e.getKey(), _ -> new PhiEntry());
          phi.setB(e.getValue().getLast());
          phi.setBNode(fail_node);
        }
      }

      // If the variable was only assigned on one path, then we need to virtually add it to the other.
      for (final var phi_entry : phiMap.entrySet()) {
        if (phi_entry.getValue().getA() == null) {
          phi_entry.getValue().setA(scope.get(phi_entry.getKey()));
          phi_entry.getValue().setANode(pass_node);
        }
        if (phi_entry.getValue().getB() == null) {
          phi_entry.getValue().setB(scope.get(phi_entry.getKey()));
          phi_entry.getValue().setBNode(fail_node);
        }
      }

      for (final var phi_entry : phiMap.entrySet()) {

        final var phi = new MirOperandPhi(
          new MirOperand[] { phi_entry.getValue().getA(), phi_entry.getValue().getB()},
          new MirNode[] { phi_entry.getValue().getANode(), phi_entry.getValue().getBNode() }
        );

        final var iid = scope.newUniqueId(phi_entry.getKey());
        final var assignment = new MirAssignment(iid, phi);

        node_merge.instructions().add(assignment);
        scope.add(assignment);
      }

      // We will always create a phi-node for the if-case. For sake of simplicity.
      // But it is up to optimization passes to remove any terminal paths.
      return new MirOperandPhi(
        new MirOperand[] { new MirOperandNodeResult(pass_node), new MirOperandNodeResult(fail_node)},
        new MirNode[] { pass_node, fail_node }
      );

    } else {

      // Both paths are terminal, so there is no need for a merge node.
      final var instruction = new MirInstrConditionalJump(predicate_operand, pass_node, fail_node);
      nodeStack.peek().instructions().add(instruction);

      return new MirOperandPhi(
        new MirOperand[] { new MirOperandNodeResult(pass_node), new MirOperandNodeResult(fail_node)},
        new MirNode[] { pass_node, fail_node }
      );
    }
  }

  private MirOperand lower_block(HirBlock hir) {

    try {

      // TODO: This is wrong, since it never does a jump to the new block node. Should it even?
      final var block_node = new MirNode("block");
      nodeStack.peek().addSuccessor(block_node);
      nodeStack.push(block_node);

      return lower_expressions(hir.children());
    } finally {
      nodeStack.pop();
    }
  }

  private MirOperand lower_argument(HirArgument hir) {
    return lower_expression(hir.expression());
  }

  private MirOperand lower_call(HirCall hir) {

    final var mirOperands = new MirOperand[hir.arguments().length];
    HirArgument[] arguments = hir.arguments();
    for (int i = 0; i < arguments.length; i++) {
      final var argument_operand = lower_expression(arguments[i]);
      if (argument_operand == null) {
        throw new IllegalArgumentException(STR."Argument '\{arguments[i]}' must produce an operand");
      } else {
        mirOperands[i] = argument_operand;
      }
    }

    final var target_operand = lower_expression(hir.functionReference());
    if (target_operand == null) {
      throw new IllegalArgumentException(STR."Function reference '\{hir.functionReference()}' must produce an operand");
    }

    final var instruction = new MirCall(target_operand, mirOperands);
    nodeStack.peek().instructions().add(instruction);

    return instruction;
  }

  private MirOperand lower_binary_operation(HirBinaryOperation hir) {

    final var lhs = lower_expression(hir.lhs());
    final var rhs = lower_expression(hir.rhs());

    final var instruction = new MirBinaryOperation(lhs, MirBinaryOperationKind.ofHirKind(hir.kind()), rhs);
    nodeStack.peek().instructions().add(instruction);

    return instruction;
  }

  private MirOperand lower_literal(HirLiteral hir) {
    return new MirOperandHirExpression(hir);
  }

  private MirOperand lower_return(HirReturn hir) {

    final var operand = lower_expression(hir.expression());
    final var instruction = new MirReturn(operand);
    nodeStack.peek().instructions().add(instruction);

    return instruction;
  }
}
