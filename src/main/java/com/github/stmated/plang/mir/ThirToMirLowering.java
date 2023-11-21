package com.github.stmated.plang.mir;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.mir.model.MirBinaryOperationKind;
import com.github.stmated.plang.mir.model.MirFnArgument;
import com.github.stmated.plang.mir.model.MirFnParameter;
import com.github.stmated.plang.mir.model.MirFnSignature;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.mir.model.MirNodeEntry;
import com.github.stmated.plang.thir.raising.ThirRaiseResult;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.TyParam;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyStruct;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueString;
import com.github.stmated.plang.ty.util.Tys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * NOTE: There will be quite some references from the MIR to the HIR, for sake of faster development compiler-side. It is however a goal to try and specialize
 * the MIR as much as possible and separate it away from the HIR.
 */
@Slf4j
public class ThirToMirLowering {

  private final ThirToMirCtx mirCtx;
  private final List<MirNodeEntry> nodes = new ArrayList<>();

  private ThirToMirLowering(ThirRaiseResult thirRaiseResult) {
    this.mirCtx = new ThirToMirCtx(null, thirRaiseResult);
  }

  ThirToMirLowering(ThirToMirCtx parent) {
    this.mirCtx = new ThirToMirCtx(parent, parent.thirRaiseResult());
  }

  /**
   * TODO: One THIR should be able to result in multiple different MirNodes.
   *        It is then up to different kinds of nodes to link them all together.
   *        Could be either a hard link to an actual MirNode, or a soft link to a locator node (like dependant on package)
   */
  public static MirLoweringResult lower(ThirRaiseResult thirRaiseResult) {

    final var lowering = new ThirToMirLowering(thirRaiseResult);

    final var entryNode = new MirNode(lowering.getNodePathName("main"));
    lowering.mirCtx.nodeStack().push(entryNode);

    final var rootScope = new MirScope(null, "root");
    lowering.mirCtx.scopeStack().push(rootScope);

    lowering.lower(lowering.mirCtx.thirRaiseResult().root());

    final var processedNode = lowering.runPostProcessPasses(entryNode);

    final var scriptInstructions = processedNode.instructions()
      .stream().filter(it -> !(it instanceof Mir.InstrCreateFn))
      .toArray();

    if (scriptInstructions.length == 0) {

      // There are no "real" instructions in the start node, so this will not be a script.
      // It is up to later stages (LLVM) to decide what th do with the different entry nodes that were found.

      return new MirLoweringResult(null, lowering.nodes.toArray(new MirNodeEntry[0]));
    } else {
      return new MirLoweringResult(processedNode, lowering.nodes.toArray(new MirNodeEntry[0]));
    }
  }

  private <T extends MirNode> T runPostProcessPasses(T node) {

    MirNodeTyPass.startPass(node);
    MirFnDeclareMovePass.startPass(node);

    return node;
  }

  Mir.Instr lower(Hir.Expression expr) {

    return switch (expr) {
      case Hir.Literal hir -> lower_literal(hir);
      case Hir.Return hir -> lower_return(hir);
      case Hir.BinaryOperation hir -> lower_binary_operation(hir);
      case Hir.Argument hir -> lower_argument(hir);
      case Hir.Call hir -> lower_call(hir);
      case Hir.Conditional hir -> lower_conditional(hir);
      case Hir.Block hir -> lower_block(hir);
      case Hir.VariableDeclaration hir -> lower_variable_declaration(hir);
      case Hir.Assignment hir -> lower_assignment(hir);
      case Hir.Identifier hir -> lower_identifier(hir);
      case Hir.Loop hir -> lower_loop(hir);
      case Hir.LoopBreak hir -> lower_loop_break(hir);
      case Hir.LoopContinue hir -> lower_loop_continue(hir);
      case Hir.Expressions v -> lower_expressions(v.children());
      case Hir.Program v -> lower(v.expressions());
      case Hir.Function it -> lower_function(it);
      case Hir.FunctionSignature it -> lower_function_signature(it);
      case Hir.Array it -> lower_array(it);
      case Hir.ArrayAccess it -> lower_array_access(it);
      case Hir.Struct it -> lower_struct(it);
      case Hir.NewByBlock it -> lower_new_by_block(it);
      case Hir.NewByCtor it -> lower_new_by_ctor(it);
      case Hir.Path it -> lower_path(it);

      default -> throw new NotImplementedException(STR."Do not know how to handle '\{expr}' (\{expr.getClass().getSimpleName()})");
    };
  }

  private Mir.Instr lower_path(Hir.Path it) {

    // The first path element must be accessible as any other base-level item, such as an identifier or type.
    final var elements = it.elements();
    return lower_path_elements(elements, 0, elements.length);
  }

  private Mir.Instr lower_path_elements(Hir.Expression[] elements, int start, int end) {

    var lastInstr = lower(elements[start]);

    for (var i = start + 1; i < end; i++) {

      final var hirPrevious = elements[i - 1];
      final var previousTy = mirCtx.thirRaiseResult().getTypeOrThrow(hirPrevious);
      final var hirElement = elements[i];
      lastInstr = switch (hirElement) {
        case Hir.Identifier id -> switch (previousTy) {
					case TyStruct struct -> {

						for (var n = 0; n < struct.fields().length; n++) {

							final var field = struct.fields()[n];
							if (field.name().equals(id.name())) {
								yield new Mir.InstrGetStructElement(lastInstr, n, field.ty());
							}
						}

						throw new IllegalArgumentException(STR."Unknown field '\{id.name()}'");
					}
					default -> throw new UnexpectedExpressionException(previousTy);
				};
        default -> throw new UnexpectedExpressionException(hirElement);
      };

      mirCtx.nodeStack().peek().instructions().add(lastInstr);
    }

    return Objects.requireNonNull(lastInstr, STR."Path '\{Arrays.toString(elements)}' could not be converted into an instruction");
  }

  private Mir.Instr lower_new_by_block(Hir.NewByBlock it) {

    final var ty = mirCtx.thirRaiseResult().getTypeOrThrow(it);
    final Mir.Instr allocatorInstr = null; // lower(it.allocator()); // TODO: Lower allocator one day

    Mir.Instr[] arguments;
    switch (ty) {
      case TyStruct struct -> {

        arguments = new Mir.Instr[struct.fields().length];
        for (var i = 0; i < struct.fields().length; i++) {

          final var field = struct.fields()[i];
          final var assignment = Arrays.stream(it.fields())
            .filter(f -> switch (f.lhs()) {
              case Hir.Identifier id -> field.name().equals(id.name());
              default -> throw new UnexpectedExpressionException(f.lhs());
            })
            .findFirst().orElseThrow(() -> new IllegalArgumentException(STR."Could not find assignment for '\{field.name()}'"));

          final var rhsInstr = lower(assignment.rhs());
          arguments[i] = rhsInstr;
        }
      }
      default -> throw new UnexpectedExpressionException(ty);
    }

    return new Mir.InstrCreateInstance(ty, allocatorInstr, arguments);
  }

  private Mir.Instr lower_new_by_ctor(Hir.NewByCtor it) {

    // TODO: This should convert into same thing as HirNewByBlock.
    //        The constructor is a function that creates a garbage version of the struct,
    //        and then fills all fields with data -- all fields must be set or struct will be in illegal state
    //        So we will create the memory for the struct (give no fields), and then call constructor function!
    //        It will then be up to some future type-safety system that makes us unable to access uninitialized fields

    throw new NotImplementedException();
  }

  private Mir.Instr lower_struct(Hir.Struct it) {

    final var ty = mirCtx.thirRaiseResult().getTypeOrThrow(it);
    assert ty instanceof TyStruct;

    final var tyStruct = (TyStruct) ty;

    return new Mir.InstrCreateStruct(tyStruct);
  }

  private Mir.Instr lower_array(Hir.Array hir) {

    final var entries = new Mir.Instr[hir.elements().length];
    for (var i = 0; i < entries.length; i++) {
      final var child = hir.elements()[i];
      entries[i] = lower(child);
    }

    final var givenTy = mirCtx.thirRaiseResult().getTypeOrThrow(hir);
    final TyValueArray arrayTy;
    final com.github.stmated.plang.ty.Ty elementTy;
    final Integer arrayLength;
    switch (givenTy) {
      case TyValueArray array -> {
        arrayTy = array;
        elementTy = array.elementType();
        arrayLength = array.size();
      }
      default -> throw new UnexpectedExpressionException(givenTy);
    }

    final Mir.Instr lengthInstr;
    if (hir.length() == null) {

      final var literalSize = new Mir.InstrCreateLiteral(Objects.toString(arrayLength), com.github.stmated.plang.ty.Ty.INTEGER);
      mirCtx.nodeStack().peek().instructions().add(literalSize);
      lengthInstr = literalSize;
    } else {
      lengthInstr = lower(hir.length());
    }

    if (lengthInstr == null) {
      throw new IllegalArgumentException(STR."There is no known size of '\{hir}'");
    }

    final var arrayInstr = new Mir.InstrCreateArray(entries, lengthInstr, arrayTy, elementTy);

    mirCtx.nodeStack().peek().instructions().add(arrayInstr);

    return arrayInstr;
  }

  private Mir.Instr lower_array_access(Hir.ArrayAccess hir) {

    final var mirTarget = lower(hir.target());
    final var mirAccessor = lower(hir.accessor());

    final var resultTy = mirCtx.thirRaiseResult().getTypeOrThrow(hir);

    final var instr = new Mir.InstrGetArrayElement(mirTarget, mirAccessor, resultTy);
    mirCtx.nodeStack().peek().instructions().add(instr);

    return instr;
  }

  private Mir.Instr lower_expressions(Hir.Expression[] expressions) {

    Mir.Instr lastOperand = null;
    for (final var expression : expressions) {
      final var operand = lower(expression);
      if (operand != null) {
        lastOperand = operand;
      }
    }

    return lastOperand;
  }

  MirFnSignature lower_function_signature_silent(Hir.FunctionSignature hirSignature) {

    final var hirParameters = hirSignature.parameters();
    final var mirParameters = new MirFnParameter[hirParameters.length];
    for (var i = 0; i < hirParameters.length; i++) {

      final var hirParameter = hirParameters[i];
      final var identifier = hirParameter.identifier();
      final var identifierName = switch (identifier) {
        case Hir.Identifier it -> it.name();
        default -> throw new NotImplementedException(STR."Do not know how to handle '\{identifier}' as parameter identifier");
      };

      final var parameterType = Objects.requireNonNullElseGet(
        mirCtx.thirRaiseResult().getType(hirParameters[i]),
        () -> expr_to_ty(hirParameter.type())
      );
      mirParameters[i] = new MirFnParameter(identifierName, parameterType);
    }

    final var returnType = Objects.requireNonNullElseGet(
      mirCtx.thirRaiseResult().getType(hirSignature.returnType()),
      () -> expr_to_ty(hirSignature.returnType())
    );

    // TODO: In the MIR stage we need to give a function a name? Or can we keep handling it as something not named?
    return new MirFnSignature(mirParameters, hirSignature.vararg(), returnType);
  }

  Mir.InstrCreateFn lower_function_signature(Hir.FunctionSignature hirSignature) {

    final var mirFnSignature = lower_function_signature_silent(hirSignature);
    final var fnTy = new TyPointer<>(signatureToTy(mirFnSignature));
    final var externalFn = new Mir.InstrCreateFn(null, mirFnSignature, fnTy);

    mirCtx.nodeStack().peek().instructions().add(externalFn);

    return externalFn;
  }

  private Mir.Instr lower_function(Hir.Function hir) {

    // TODO: Is this odd or correct? Maybe it is correct and it is up to MIR -> LLVM stage to move the function declaration to earlier?
    //        I guess that should only be made for "const" things -- and that will be how to differentiate between what you can refer to before declaration?
    //        Sounds like a good idea...

    var mirFnSignature = lower_function_signature_silent(hir.signature());

    // fn name is extremely likely to be null.
    // It is up to some later pass to add a more descriptive name if possible.

    final var fnNode = new MirNodeEntry(null, mirFnSignature);

    final var offshoot = new ThirToMirLowering(this.mirCtx);
    offshoot.mirCtx.nodeStack().add(fnNode);

    final var fnScope = new MirScope(null, "fn_root");
    offshoot.mirCtx.scopeStack().add(fnScope);

//    for (final var parameter : mirFnSignature.parameters()) {
//      fnScope.add();
//    }
    // TODO: Need a good way to add parent scopes that are dynamic and make it easy to understand!

    final var bodyInstruction = offshoot.lower(hir.body());
    var processedNode = offshoot.runPostProcessPasses(fnNode);

    if (mirFnSignature.returnType() == com.github.stmated.plang.ty.Ty.INFER) {

      final var actualTy = switch (mirCtx.thirRaiseResult().getType(hir)) {
        case TyFn it -> it.returnTy();
        default -> throw new IllegalArgumentException("Cannot infer the result ty");
      };

      mirFnSignature = new MirFnSignature(
        mirFnSignature.parameters(),
        mirFnSignature.vararg(),
        actualTy
      );

      final var original = processedNode;
      processedNode = new MirNodeEntry(processedNode.name(), mirFnSignature);
      processedNode.instructions().addAll(original.instructions());
      processedNode.predecessors().addAll(original.predecessors());
      processedNode.successors().addAll(original.successors());
    }

    nodes.add(processedNode);

    final var fnTy = signatureToTy(mirFnSignature);
    final var fullFn = new Mir.InstrCreateFn(processedNode, mirFnSignature, new TyPointer<>(fnTy));

    mirCtx.nodeStack().peek().instructions().add(fullFn);

    return fullFn;
  }

  public static TyFn signatureToTy(MirFnSignature mirFnSignature) {
    final var paramTys = new TyParam[mirFnSignature.parameters().length];
    for (var i = 0; i < mirFnSignature.parameters().length; i++) {
      final var parameter = mirFnSignature.parameters()[i];
      paramTys[i] = new TyParam(parameter.name(), parameter.ty());
    }

    return new TyFn(paramTys, mirFnSignature.vararg(), mirFnSignature.returnType());
  }

  com.github.stmated.plang.ty.Ty expr_to_ty(Hir.Expression hir) {

    return switch (hir) {
      case Hir.TyExpr it -> it.ty();
      case Hir.Literal literal -> switch (literal.ty()) {
        case TyValueString _ -> Tys.fromString(literal.content());
        default -> throw new IllegalArgumentException(STR."Not valid literal '\{literal}'");
      };
      default -> this.mirCtx.thirRaiseResult().getTypeOrThrow(hir);
    };
  }

  private String getNodePathName(String name) {

    final var sb = new StringBuilder();

    if (mirCtx.nodeStack().size() > 1) {

      final var parent = mirCtx.nodeStack().peek();
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

  private Mir.Instr lower_loop(Hir.Loop hir) {

    final var node_loop = new MirNode(getNodePathName("loop_body"));
    mirCtx.nodeStack().peek().addSuccessor(node_loop);

    final var node_after = new MirNode(getNodePathName("loop_after"));

    final var jump = new Mir.InstrJump(node_loop);
    mirCtx.nodeStack().peek().instructions().add(jump);

    final var loop_handle = new ThirToMirCtx.LoopHandle(node_loop, node_after);
    final var loop_scope = new MirScope(mirCtx.scopeStack().peek(), "loop");

    try {
      mirCtx.scopeStack().push(loop_scope);
      try {
        mirCtx.loopStack().push(loop_handle);
        try {
          mirCtx.nodeStack().push(node_loop);
          return lower(hir.body());
        } finally {

          // We are done with current node, node_after is the future.
          // It is up to later optimization stages to remove if it turns out empty.
          mirCtx.nodeStack().pop();

          // Think of it as less of a nested stack and more like a pointer to the where the writing head it.
          mirCtx.nodeStack().push(node_after);
        }
      } finally {
        mirCtx.loopStack().pop();
      }
    } finally {
      mirCtx.scopeStack().pop();
    }
  }

  private Mir.Instr lower_loop_continue(Hir.LoopContinue hir) {

    final var loop = mirCtx.loopStack().peek();
    final var node = mirCtx.nodeStack().peek();

    final var jump = new Mir.InstrJump(loop.next());
    node.successors().add(loop.next());
    loop.next().predecessors().add(node);
    node.instructions().add(jump);

    return null;
  }

  private Mir.Instr lower_loop_break(Hir.LoopBreak hir) {

    final var loop = mirCtx.loopStack().peek();
    final var node = mirCtx.nodeStack().peek();

    final var jump = new Mir.InstrJump(loop.exit());
    node.successors().add(loop.exit());
    loop.exit().predecessors().add(node);
    node.instructions().add(jump);

    return null;
  }

  private Mir.Instr lower_identifier(Hir.Identifier hir) {

    final var identifierName = hir.name();

    final var assignment = mirCtx.scopeStack().peek().get(identifierName);
    if (assignment != null) {
      return assignment;
    }

    final var paramInstr = getParamGetInstr(identifierName);
    if (paramInstr != null) {
      mirCtx.nodeStack().peek().instructions().add(paramInstr);
      return paramInstr;
    }

    throw new IllegalArgumentException(STR."There is no variable '\{hir.name()}' found in scope");
  }

  private Mir.InstrGetParam getParamGetInstr(String identifierName) {

    final var fnIterator = mirCtx.nodeStack().descendingIterator();
    while (fnIterator.hasNext()) {

      final var fn = fnIterator.next();
      if (fn instanceof MirNodeEntry entry) {

        for (var i = 0; i < entry.fnSignature().parameters().length; i++) {

          final var param = entry.fnSignature().parameters()[i];
          if (param.name().equals(identifierName)) {

            return new Mir.InstrGetParam(param);
          }
        }
      }
    }

    return null;
  }

  private Mir.Instr lower_assignment(Hir.Assignment hir) {

    return switch (hir.lhs()) {
      case Hir.VariableDeclaration lhs -> lower_assignment_root_level(hir, lhs.identifier().name(), true);
      case Hir.Identifier lhs -> lower_assignment_root_level(hir, lhs.name(), false);
      case Hir.Path lhs -> lower_assignment_to_path(hir, lhs);
      default -> throw new UnexpectedExpressionException(hir.lhs());
    };
  }

  private Mir.Instr lower_assignment_to_path(Hir.Assignment it, Hir.Path path) {

    // Convert the path into a get instruction, except for the last element which we write to.
    final var get_instruction = lower_path_elements(path.elements(), 0, path.elements().length - 1);



    var lastInstr = lower(path.elements()[0]);

    for (var i = 1; i < path.elements().length; i++) {

      final var hirPrevious = path.elements()[i - 1];
      final var previousTy = mirCtx.thirRaiseResult().getTypeOrThrow(hirPrevious);
      final var hirElement = path.elements()[i];
      lastInstr = switch (hirElement) {
        case Hir.Identifier id -> switch (previousTy) {
          case TyStruct struct -> {

            for (var n = 0; n < struct.fields().length; n++) {

              final var field = struct.fields()[n];
              if (field.name().equals(id.name())) {
                yield new Mir.InstrGetStructElement(lastInstr, n, field.ty());
              }
            }

            throw new IllegalArgumentException(STR."Unknown field '\{id.name()}'");
          }
          default -> throw new UnexpectedExpressionException(previousTy);
        };
        default -> throw new UnexpectedExpressionException(hirElement);
      };

      mirCtx.nodeStack().peek().instructions().add(lastInstr);
    }

    return Objects.requireNonNull(lastInstr, STR."Path '\{it}' could not be converted into an instruction");
  }

  private Mir.Instr lower_assignment_root_level(Hir.Assignment hir, String name, boolean declare) {

    // TODO: If it is a function, then if it is "const" then save it to global and store that
    final var rhs = lower(hir.rhs());
    final var scope = mirCtx.scopeStack().peek();
    final var scopeValue = scope.get(name);

    // TODO: Make use of "declare" again, in whatever way is needed

    // TODO: This code below is wrong and too specific. Can probably be generalized somehow.
    //        Maybe it is enough if the ty of the rhs is a pointer (or pointer-like as array or string) then just store that.
    Mir.Instr instr;
    if (rhs instanceof Mir.InstrCreateArray ica) {
      instr = ica;
    } else if (rhs instanceof Mir.InstrStore is) {
      instr = is;
    } else if (rhs instanceof Mir.InstrCreateFn is) {
      instr = is;
    } else {
      if (scopeValue == null) {
        final var is = new Mir.InstrStore(null, rhs, new TyPointer<>(rhs.ty()));
        is.name(new MirIdentifierId(name, null, 0));
        instr = is;
      } else if (scopeValue instanceof Mir.InstrStore originalStore) {
        instr = new Mir.InstrStore(originalStore, rhs, new TyPointer<>(rhs.ty()));
      } else {
        throw new IllegalArgumentException("Should this be allowed to happen?");
      }

      mirCtx.nodeStack().peek().instructions().add(instr);
    }

    // TODO: Should this actually be needed at all? Should it not be deduced some other way?
    scope.add(name, instr);

    return instr;
  }

  private Mir.Instr lower_variable_declaration(Hir.VariableDeclaration hir) {

    log.debug("Variable declaration has no meaning in CFG, handle assignment expressions");
    return null;
  }

  private void addConnection(MirNode source, MirNode destination) {

    source.successors().add(destination);
    destination.predecessors().add(source);
  }

  @Data
  private class PhiEntry {

    Mir.Instr a;
    MirNode aNode;
    Mir.Instr b;
    MirNode bNode;
  }

  private Mir.Instr lower_conditional(Hir.Conditional hir) {

    final var predicate_operand = lower(hir.predicate());

    final var scope = mirCtx.scopeStack().peek();

    MirScope pass_scope;
    MirNode pass_node = new MirNode(getNodePathName("conditional_pass"));
    try {

      mirCtx.nodeStack().peek().addSuccessor(pass_node);
      mirCtx.nodeStack().push(pass_node);

      pass_scope = new MirScope(mirCtx.scopeStack().peek(), getNodePathName("scope_conditional_pass"));
      try {
        mirCtx.scopeStack().push(pass_scope);
        if (hir.pass() != null) {
          final var branch_operand = lower(hir.pass());
        }
      } finally {
        mirCtx.scopeStack().pop();
      }
    } finally {
      mirCtx.nodeStack().pop();
    }

    MirScope fail_scope;
    MirNode fail_node = new MirNode(getNodePathName("conditional_fail"));
    try {

      mirCtx.nodeStack().peek().addSuccessor(fail_node);
      mirCtx.nodeStack().push(fail_node);

      fail_scope = new MirScope(mirCtx.scopeStack().peek(), "scope_conditional_fail");
      try {
        mirCtx.scopeStack().push(fail_scope);
        if (hir.fail() != null) {
          final var branch_operand = lower(hir.fail());
        }
      } finally {
        mirCtx.scopeStack().pop();
      }
    } finally {
      mirCtx.nodeStack().pop();
    }

    if (!pass_node.isTerminal() || !fail_node.isTerminal()) {

      final var parent = mirCtx.nodeStack().pop();
      final var node_merge = new MirNode(getNodePathName("conditional_merge"));
      mirCtx.nodeStack().push(node_merge); // TODO: Is this really correct? Does not seem so!

      if (!pass_node.isTerminal()) {

        pass_node.instructions().add(new Mir.InstrJump(node_merge));
        addConnection(pass_node, node_merge);
      }

      if (!fail_node.isTerminal()) {

        fail_node.instructions().add(new Mir.InstrJump(node_merge));
        addConnection(fail_node, node_merge);
      }

      final var instruction = new Mir.InstrConditionalJump(predicate_operand, pass_node, fail_node);
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

        final var phi = new Mir.InstrPhi(
          new Mir.Instr[]{a, b},
          new MirNode[]{phi_entry.getValue().aNode(), phi_entry.getValue().bNode()},
          phiTy
        );

        mirCtx.nodeStack().peek().instructions().add(phi);
        scope.add(phi_entry.getKey(), phi);
      }

      // We will always create a phi-node for the if-case. For sake of simplicity.
      // But it is up to optimization passes to remove any terminal paths.

      return getMirInstrPhi(hir, pass_node, fail_node);
    }

    final var instruction = new Mir.InstrConditionalJump(predicate_operand, pass_node, fail_node);
    mirCtx.nodeStack().peek().instructions().add(instruction);

    return (pass_node.isTerminal() && fail_node.isTerminal())
      // NOTE: Most likely wrong; should return the instruction/void. Up to caller if that instruction should be used (which it should not)
      ? null
      : getMirInstrPhi(hir, pass_node, fail_node);
  }

  private Mir.Instr getMirInstrPhi(Hir.Conditional hir, MirNode pass_node, MirNode fail_node) {

    final var resultTy = Objects.requireNonNull(mirCtx.thirRaiseResult().getType(hir), "Conditional itself must have a result kind");

    // TODO: Need a "good" way of solving a phi with only one path because of conditional, until a Result(T) kind is added

    if (resultTy != com.github.stmated.plang.ty.Ty.VOID) {

      final var last_pass = getLastValueInstruction(pass_node);
      final var last_fail = getLastValueInstruction(fail_node);

      if (last_pass == null || last_fail == null) {

        // Do not create a phi node as result instead give the last value instruction.
        // This would happen if only one of the branches yield a value.
        // TODO: This should yield a Result/Optional/other construct, where you would need to verify the result? Need better test cases.
        return Objects.requireNonNullElse(last_pass, last_fail);
      }

      final var phi = new Mir.InstrPhi(
        new Mir.Instr[]{last_pass, last_fail},
        new MirNode[]{pass_node, fail_node},
        resultTy
      );

      mirCtx.nodeStack().peek().instructions().add(phi);
      return phi;
    }

    // NOTE: This is likely wrong. It should return something. An always failing Result/Optional/other construct?
    return null;
  }

  private Mir.Instr getLastValueInstruction(MirNode node) {

    for (var i = node.instructions().size() - 1; i >= 0; i--) {

      final var instr = node.instructions().get(i);
      if (instr instanceof Mir.InstrJump jump) {
        continue;
        //return getLastValueInstruction(jump.node());
      } else if (instr instanceof Mir.InstrConditionalJump) {
        // TODO: Can this be solved?
        continue;
      } else {
        return instr;
      }
    }

    return null;
//    throw new IllegalArgumentException(STR."There were no value instructions for node '\{node}'");
  }

  private Mir.Instr lower_block(Hir.Block hir) {

    // TODO: This is wrong, since it never does a jump to the new block node. Should it even?
//    final var block_node = new MirNode(getNodePathName("block"));
//    final var block_scope = new MirScope(mirCtx.scopeStack().peek(), "block");

//    try {
//
////      mirCtx.scopeStack().push(block_scope);
//      try {

//        final var parent = mirCtx.nodeStack().pop();

//        parent.addSuccessor(block_node);
//        parent.instructions().add(new InstrJump(block_node));

//        mirCtx.nodeStack().push(block_node);

        return lower(hir.children());
//      } finally {
////        mirCtx.nodeStack().pop();
//      }
//    } finally {
//
//      // Any values inside here are lost since we just pop it
//
////      mirCtx.scopeStack().pop();
//    }
  }

  private Mir.Instr lower_argument(Hir.Argument hir) {
    return lower(hir.value());
  }

  private Mir.Instr lower_call(Hir.Call hir) {

    final var mirFnArguments = new MirFnArgument[hir.arguments().length];
    Hir.Argument[] arguments = hir.arguments();
    for (int i = 0; i < arguments.length; i++) {
      final var argument_operand = lower(arguments[i]);
      if (argument_operand == null) {
        throw new IllegalArgumentException(STR."Argument '\{arguments[i]}' must produce an operand");
      } else {
        mirFnArguments[i] = new MirFnArgument(arguments[i].label(), argument_operand);
      }
    }

    if (hir.target() instanceof Hir.Function) {

      final var createFnInstr = lower(hir.target());
      return switch (createFnInstr) {
        case Mir.InstrCreateFn createFn -> {

          final var instruction = new Mir.InstrCall(createFnInstr, createFn.signature(), mirFnArguments);
          mirCtx.nodeStack().peek().instructions().add(instruction);
          yield instruction;
        }
        default -> throw new IllegalArgumentException(
          STR."The target is a \{Hir.Function.class.getSimpleName()} but did not lower to \{Mir.InstrCreateFn.class.getSimpleName()}"
        );
      };
    }

    // TODO: This is very rudimentary -- it needs to be able to resolve the a potential path into a struct, or array access, or whatever the heck
    final var fnName = switch (hir.target()) {
      case Hir.Identifier identifier -> identifier.name();
      case Hir.Literal literal -> literal.content();
      default -> throw new UnexpectedExpressionException(hir.target());
    };

    final var fnInstr = Objects.requireNonNull(
      mirCtx.scopeStack().peek().get(fnName),
      STR."No such function '\{fnName}' found"
    );

    return switch (fnInstr) {
      // NOTE: Unsure how this will be handled -- but I guess it will be an indirect function pointer?
      //        So it will be up to LLVM to decide what to do about it.
      case Mir.InstrStore store -> switch (store.value()) {
        case Mir.InstrCreateFn createFn -> {

          final var instruction = new Mir.InstrCall(fnInstr, createFn.signature(), mirFnArguments);
          mirCtx.nodeStack().peek().instructions().add(instruction);
          yield instruction;
        }
        default -> throw new UnexpectedExpressionException(store.value());
      };
      case Mir.InstrCreateFn createFn -> {
        final var instruction = new Mir.InstrCall(fnInstr, createFn.signature(), mirFnArguments);
        mirCtx.nodeStack().peek().instructions().add(instruction);
        yield instruction;
      }
      default -> throw new UnexpectedExpressionException(fnInstr);
    };
  }

  private Mir.Instr lower_binary_operation(Hir.BinaryOperation hir) {

    final var lhs = lower(hir.lhs());
    final var rhs = lower(hir.rhs());

    final var mirBopKind = MirBinaryOperationKind.ofHirKind(hir.kind());
    final var ty = mirCtx.thirRaiseResult().getTypeOrThrow(hir);
    final var instruction = new Mir.InstrBinaryOperation(lhs, mirBopKind, rhs, ty);
    mirCtx.nodeStack().peek().instructions().add(instruction);

    final var scope = mirCtx.scopeStack().peek();
    final var iid = scope.newUniqueId("bop");
    instruction.name(iid);

    return instruction;
  }

  private Mir.Instr lower_literal(Hir.Literal hir) {

    final var ty = Objects.requireNonNullElse(mirCtx.thirRaiseResult().getType(hir), hir.ty());
    final var instruction = new Mir.InstrCreateLiteral(hir.content(), ty);

    mirCtx.nodeStack().peek().instructions().add(instruction);

    return instruction;
  }

  private Mir.Instr lower_return(Hir.Return hir) {

    final var operand = lower(hir.expression());
    if (operand == null) {
      log.warn("Return operand is null. Likely because of invalid AST->HIR raising implicit return. Build is-terminal visitor");
      return null;
    }

    final var instruction = new Mir.InstrReturn(operand);
    mirCtx.nodeStack().peek().instructions().add(instruction);

    return instruction;
  }
}
