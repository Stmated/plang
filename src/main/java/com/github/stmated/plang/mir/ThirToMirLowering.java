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
import com.github.stmated.plang.hir.model.HirFunctionReference;
import com.github.stmated.plang.hir.model.HirIdentifier;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirLoop;
import com.github.stmated.plang.hir.model.HirLoopBreak;
import com.github.stmated.plang.hir.model.HirLoopContinue;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import com.github.stmated.plang.hir.model.HirVariableDeclaration;
import com.github.stmated.plang.mir.model.MirBinaryOperationKind;
import com.github.stmated.plang.mir.model.MirCall;
import com.github.stmated.plang.mir.model.MirFn;
import com.github.stmated.plang.mir.model.MirFnArgument;
import com.github.stmated.plang.mir.model.MirFnParameter;
import com.github.stmated.plang.mir.model.MirInstr;
import com.github.stmated.plang.mir.model.MirInstrBinaryOperation;
import com.github.stmated.plang.mir.model.MirInstrConditionalJump;
import com.github.stmated.plang.mir.model.MirInstrCreateLiteral;
import com.github.stmated.plang.mir.model.MirInstrJump;
import com.github.stmated.plang.mir.model.MirInstrPhi;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.mir.model.MirReturn;
import com.github.stmated.plang.thir.raising.ThirRepository;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.util.Tys;
import java.util.HashMap;
import java.util.Objects;
import java.util.Stack;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * NOTE: There will be quite some references from the MIR to the HIR, for sake of faster development compiler-side. It is however a goal to try and specialize
 * the MIR as much as possible and separate it away from the HIR.
 */
@Slf4j
public class ThirToMirLowering {

  private record LoopHandle(MirNode next, MirNode exit) {

  }

  private final Stack<LoopHandle> loopStack = new Stack<>();

  private final Stack<MirNode> nodeStack = new Stack<>();
  private final Stack<MirScope> scopeStack = new Stack<>();

  private final ThirRepository thirRepository;

  public ThirToMirLowering(ThirRepository thirRepository) {
    this.thirRepository = thirRepository;
  }

  /**
   * TODO: One THIR should be able to result in multiple different MirNodes.
   *        It is then up to different kinds of nodes to link them all together.
   *        Could be either a hard link to an actual MirNode, or a soft link to a locator node (like dependant on package)
   */
  public MirNode lower() {

    final var startNode = new MirNode(getNodePathName("start"));
    final var startScope = new MirScope(null, "start");

    try {
      scopeStack.push(startScope);
      try {
        nodeStack.push(startNode);
        lower_expression(thirRepository.root());
      } finally {
        nodeStack.pop();
      }
    } finally {
      scopeStack.pop();
    }

    return startNode;
  }

  private MirInstr lower_expressions(HirExpression[] expressions) {

    MirInstr lastOperand = null;
    for (final var expression : expressions) {
      final var operand = lower_expression(expression);
      if (operand != null) {
        lastOperand = operand;
      }
    }

    return lastOperand;
  }

  private MirInstr lower_expression(HirExpression expr) {

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

  private String getNodePathName(String name) {

    final var sb = new StringBuilder();

    if (nodeStack.size() > 1) {

      final var parent = nodeStack.peek();
      if (parent != null) {
        sb.append(parent.name());

        if (!sb.isEmpty()) {
          sb.append("_");
        }
      }
    }

    sb.append(name);

    return sb.toString();
  }

  private MirInstr lower_loop(HirLoop hir) {

    final var node_loop = new MirNode(getNodePathName("loop_body"));
    nodeStack.peek().addSuccessor(node_loop);

    final var node_after = new MirNode(getNodePathName("loop_after"));

    final var jump = new MirInstrJump(node_loop);
    nodeStack.peek().instructions().add(jump);

    final var loop_handle = new LoopHandle(node_loop, node_after);
    final var loop_scope = new MirScope(scopeStack.peek(), "loop");

    try {
      scopeStack.push(loop_scope);
      try {
        loopStack.push(loop_handle);
        try {
          nodeStack.push(node_loop);
          return lower_expression(hir.body());
        } finally {

          // We are done with current node, node_after is the future.
          // It is up to later optimization stages to remove if it turns out empty.
          nodeStack.pop();

          // TODO: This seems very bad, it will never get properly popped off!
          nodeStack.push(node_after);
        }
      } finally {
        loopStack.pop();
      }
    } finally {
      scopeStack.pop();
    }
  }

  private MirInstr lower_loop_continue(HirLoopContinue hir) {

    final var loop = loopStack.peek();
    final var node = nodeStack.peek();

    final var jump = new MirInstrJump(loop.next());
    node.successors().add(loop.next());
    loop.next().predecessors().add(node);
    node.instructions().add(jump);

    return null;
  }

  private MirInstr lower_loop_break(HirLoopBreak hir) {

    final var loop = loopStack.peek();
    final var node = nodeStack.peek();

    final var jump = new MirInstrJump(loop.exit());
    node.successors().add(loop.exit());
    loop.exit().predecessors().add(node);
    node.instructions().add(jump);

    return null;
  }

  private MirInstr lower_identifier(HirIdentifier hir) {

    final var identifierName = hir.name();

    final var assignment = scopeStack.peek().get(identifierName);
    if (assignment != null) {
      return assignment;
    }

    throw new IllegalArgumentException(STR."There is no variable '\{hir.name()}' found in scope");
  }

  private MirInstr lower_assignment(HirAssignment hir) {

    final String name;
    final boolean declare;
    switch (hir.lhs()) {
      case HirVariableDeclaration lhs -> {
        name = lhs.identifier().name();
        declare = true;
      }
      case HirIdentifier lhs -> {
        name = lhs.name();
        declare = false;
      }
      default -> throw new UnexpectedExpressionException(hir.lhs());
    }

    final var rhs = lower_expression(hir.rhs());
    final var scope = scopeStack.peek();
    final var iid = scope.newUniqueId(name);

    rhs.name(iid);
    scope.add(rhs);

    return rhs;
  }

  private MirInstr lower_variable_declaration(HirVariableDeclaration hir) {

    log.debug("Variable declaration has no meaning in CFG, handle assignment expressions");
    return null;
  }

  private void addConnection(MirNode source, MirNode destination) {

    source.successors().add(destination);
    destination.predecessors().add(source);
  }

  @Data
  private class PhiEntry {

    MirInstr a;
    MirNode aNode;
    MirInstr b;
    MirNode bNode;
  }

  private MirInstr lower_conditional(HirConditional hir) {

    final var predicate_operand = lower_expression(hir.predicate());

    final var scope = scopeStack.peek();

    MirScope pass_scope;
    MirNode pass_node = new MirNode(getNodePathName("conditional_pass"));
    try {

      nodeStack.peek().addSuccessor(pass_node);
      nodeStack.push(pass_node);

      pass_scope = new MirScope(scopeStack.peek(), getNodePathName("scope_conditional_pass"));
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
    MirNode fail_node = new MirNode(getNodePathName("conditional_fail"));
    try {

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
      final var node_merge = new MirNode(getNodePathName("conditional_merge"));
      nodeStack.push(node_merge); // TODO: Is this really correct? Does not seem so!

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
          phi
            .a(e.getValue().getLast())
            .aNode(pass_node);
        }
      }

      for (final var e : fail_scope.map().entrySet()) {
        if (scope.get(e.getKey()) != null) {
          final var phi = phiMap.computeIfAbsent(e.getKey(), _ -> new PhiEntry());
          phi
            .b(e.getValue().getLast())
            .bNode(fail_node);
        }
      }

      // If the variable was only assigned on one path, then we need to virtually add it to the other.
      for (final var phi_entry : phiMap.entrySet()) {
        if (phi_entry.getValue().a() == null) {
          phi_entry.getValue().a(scope.get(phi_entry.getKey()));
          phi_entry.getValue().aNode(pass_node);
        }
        if (phi_entry.getValue().b() == null) {
          phi_entry.getValue()
            .b(scope.get(phi_entry.getKey()))
            .bNode(fail_node);
        }
      }

      for (final var phi_entry : phiMap.entrySet()) {

        final var a = phi_entry.getValue().a();
        final var b = phi_entry.getValue().b();

        final var phiTy = Tys.merge(a.ty(), b.ty());

        if (!Tys.isUsable(phiTy)) {
          throw new IllegalArgumentException(STR."Merged Phi node for '\{a}' and '\{b}' not usable");
        }

        final var phi = new MirInstrPhi(
          new MirInstr[]{a, b},
          new MirNode[]{phi_entry.getValue().aNode(), phi_entry.getValue().bNode()},
          phiTy
        );

        final var iid = scope.newUniqueId(phi_entry.getKey());
        phi.name(iid);

        nodeStack.peek().instructions().add(phi);
        scope.add(phi);
      }

      // We will always create a phi-node for the if-case. For sake of simplicity.
      // But it is up to optimization passes to remove any terminal paths.

      return getMirInstrPhi(hir, pass_node, fail_node);

    } else if (pass_node.isTerminal() && fail_node.isTerminal()) {

      final var instruction = new MirInstrConditionalJump(predicate_operand, pass_node, fail_node);
      nodeStack.peek().instructions().add(instruction);

      return null;

    } else {

      // Both paths are terminal, so there is no need for a merge node.
      final var instruction = new MirInstrConditionalJump(predicate_operand, pass_node, fail_node);
      nodeStack.peek().instructions().add(instruction);

      // TODO: Should probably return something? Even if just some kind of "void" instruction?
      return getMirInstrPhi(hir, pass_node, fail_node);
    }
  }

  private MirInstr getMirInstrPhi(HirConditional hir, MirNode pass_node, MirNode fail_node) {

    final var resultTy = Objects.requireNonNull(thirRepository.getType(hir), "Conditional itself must have a result type");

    // TODO: Need a "good" way of solving a phi with only one path because of conditional, until a Result(T) type is added

    if (resultTy != Ty.VOID) {

//      final var passTy = Objects.requireNonNull(thirRepository.getType(hir.pass()), "Pass branch must have a result type");
//      final var failTy = Objects.requireNonNull(thirRepository.getType(hir.fail()), "Fail branch must have a result type");

      final var last_pass = getLastValueInstruction(pass_node);
      final var last_fail = getLastValueInstruction(fail_node);

      if (last_pass == null || last_fail == null) {

        // Do not create a phi node as result instead give the last value instruction.
        // This would happen if only one of the branches yield a value.
        // TODO: This should yield a Result/Optional/other construct, where you would need to verify the result? Need better test cases.
        return Objects.requireNonNullElse(last_pass, last_fail);
      }

      final var phi = new MirInstrPhi(
        new MirInstr[]{last_pass, last_fail},
        new MirNode[]{pass_node, fail_node},
        resultTy
      );

      nodeStack.peek().instructions().add(phi);
      return phi;
    }

    // NOTE: This is likely wrong. It should return something. An always failing Result/Optional/other construct?
    return null;
  }

  private MirInstr getLastValueInstruction(MirNode node) {

    for (var i = node.instructions().size() - 1; i >= 0; i--) {

      final var instr = node.instructions().get(i);
      if (instr instanceof MirInstrJump || instr instanceof MirInstrConditionalJump) {
        continue;
      } else {
        return instr;
      }
    }

    return null;
//    throw new IllegalArgumentException(STR."There were no value instructions for node '\{node}'");
  }

  private MirInstr lower_block(HirBlock hir) {

    // TODO: This is wrong, since it never does a jump to the new block node. Should it even?
    final var block_node = new MirNode(getNodePathName("block"));
    final var block_scope = new MirScope(scopeStack.peek(), "block");

    try {

      scopeStack.push(block_scope);
      try {

        nodeStack.peek().addSuccessor(block_node);
        nodeStack.push(block_node);

        return lower_expressions(hir.children());
      } finally {
        nodeStack.pop();
      }
    } finally {
      scopeStack.pop();
    }
  }

  private MirInstr lower_argument(HirArgument hir) {
    return lower_expression(hir.value());
  }

  private MirInstr lower_call(HirCall hir) {

    final var mirFnArguments = new MirFnArgument[hir.arguments().length];
    HirArgument[] arguments = hir.arguments();
    for (int i = 0; i < arguments.length; i++) {
      final var argument_operand = lower_expression(arguments[i]);
      if (argument_operand == null) {
        throw new IllegalArgumentException(STR."Argument '\{arguments[i]}' must produce an operand");
      } else {
        mirFnArguments[i] = new MirFnArgument(arguments[i].label(), argument_operand);
      }
    }

    final var mirFn = getMirFn(hir.functionReference());

    final var instruction = new MirCall(
      mirFn,
      mirFnArguments,
      Objects.requireNonNullElseGet(
        hir.functionReference().function().returnType(),
        () -> thirRepository.getType(hir)
      )
    );

    nodeStack.peek().instructions().add(instruction);

    return instruction;
  }

  private MirFn getMirFn(HirFunctionReference hirFn) {

    // TODO: We should not be creating the function here, we should look it up somehow.
    //        Perhaps we lock the thread here and await on a promise that might resolve the function reference?

    final var hirParameters = hirFn.function().parameters();
    final var mirParameters = new MirFnParameter[hirParameters.length];
    for (var i = 0; i < hirParameters.length; i++) {
      final var identifier = hirParameters[i].identifier();
      final var identifierName = (identifier == null) ? null : identifier.name();
      final var parameterType = Objects.requireNonNullElse(
        thirRepository.getType(hirParameters[i]),
        hirParameters[i].ty()
      );
      mirParameters[i] = new MirFnParameter(identifierName, parameterType);
    }

    return new MirFn(
      hirFn.function().identifier().name(),
      // TODO: This needs to be a reference to the entry node of the other function. This is where it starts getting tricky.
      //        For external functions there is no entry, it will be made available through the linker
      null,
      mirParameters,
      hirFn.function().vararg(),
      hirFn.function().returnType()
    );

//    return switch (hirFn.function().identifier().name()) {
//      case "printf" -> {
//        //        final var i8PointerType = LLVM.LLVMPointerType(getType("i8"), 0);
////        LLVMTypeRef[] printfArgs = {i8PointerType};
////        final var printFnType = LLVM.LLVMFunctionType(getType("i32"), new PointerPointer<>(printfArgs), printfArgs.length, 1);
////        final var printfFn = LLVM.LLVMAddFunction(module, "printf", printFnType);
////
////        return new ExternalFn(printfFn, printFnType);
//
//        yield new MirFn(
//          hirFn.function().identifier().name(),
//          null,
//          mirParameters,
//          hirFn.function().returnType()
//        );
//      }
//      default -> {
//        yield
//      }
//    };
  }

  private MirInstr lower_binary_operation(HirBinaryOperation hir) {

    final var lhs = lower_expression(hir.lhs());
    final var rhs = lower_expression(hir.rhs());

    final var mirBopKind = MirBinaryOperationKind.ofHirKind(hir.kind());
    final var ty = thirRepository.getTypeOrThrow(hir);
    final var instruction = new MirInstrBinaryOperation(lhs, mirBopKind, rhs, ty);
    nodeStack.peek().instructions().add(instruction);

    final var scope = scopeStack.peek();
    final var iid = scope.newUniqueId("bop");
    instruction.name(iid);

    return instruction;
  }

  private MirInstr lower_literal(HirLiteral hir) {
    final var ty = Objects.requireNonNullElse(thirRepository.getType(hir), hir.ty());
    final var instruction = new MirInstrCreateLiteral(hir.content(), ty);

    nodeStack.peek().instructions().add(instruction);

    return instruction;
  }

  private MirInstr lower_return(HirReturn hir) {

    final var operand = lower_expression(hir.expression());
    if (operand == null) {
      log.warn("Return operand is null. Likely because of invalid AST->HIR raising implicit return. Build is-terminal visitor");
      return null;
    }

    final var instruction = new MirReturn(operand);
    nodeStack.peek().instructions().add(instruction);

    return instruction;
  }
}
