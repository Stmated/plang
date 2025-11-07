package org.inf.mir;

import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.inf.exceptions.NotImplementedException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.llvm.util.LLVMTys;
import org.inf.mir.model.*;
import org.inf.thir.raising.ThirRaiseResult;
import org.inf.ty.*;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * NOTE: There will be quite some references from the MIR to the HIR, for sake of faster development compiler-side. It is however a goal to try and specialize
 * the MIR as much as possible and separate it away from the HIR.
 */
@Slf4j
public class ThirToMirLowering {

  private final ThirToMirCtx mirCtx;

  private ThirToMirLowering(ThirRaiseResult thirRaiseResult, MachineTarget machineTarget) {
    this.mirCtx = new ThirToMirCtx(null, machineTarget, thirRaiseResult);
  }

  ThirToMirLowering(ThirToMirCtx parent, MachineTarget machineTarget) {
    this.mirCtx = new ThirToMirCtx(parent, machineTarget, parent.thirRaiseResult());
  }

  /**
   * TODO: One THIR should be able to result in multiple different MirNodes.
   *        It is then up to different kinds of nodes to link them all together.
   *        Could be either a hard link to an actual MirNode, or a soft link to a locator node (like dependant on package)
   */
  public static MirLoweringResult lower(ThirRaiseResult thirRaiseResult, MachineTarget machineTarget) {

    final var lowering = new ThirToMirLowering(thirRaiseResult, machineTarget);

    final var entryNode = new MirNode(lowering.getNodePathName("main"));
    lowering.mirCtx.nodeStack().push(entryNode);

    final var rootScope = new MirScope(null, "root");
    lowering.mirCtx.scopeStack().push(rootScope);

    // TODO: This should be done for all constants, and in a smarter way. We should re-order expressions based on usage.
    //        But to get around recursive dependencies between functions we would still need to create the function definitions on a first pass.
    thirRaiseResult.root().visit(new HirVisitor() {

      private String fnName;

      @Override
      public void visitAssignment(Hir.Assignment expr) {

        if (expr.rhs().ty() instanceof TyFn rhs_tyFn) {
          if (expr.lhs() instanceof Hir.Dec lhs_dec) {
            if (lhs_dec.mutabilityKind() == Hir.MutabilityKind.IMMUTABLE || lhs_dec.mutabilityKind() == Hir.MutabilityKind.CONSTANT) {

              try {
                fnName = lhs_dec.lexeme().name();
                HirVisitor.super.visitAssignment(expr);
              } finally {
                fnName = null;
              }
            }
          } else if (expr.lhs() instanceof Hir.Identifier lhs_id) {

            // We do not care, since if this is possible then it is not immutable nor constant.
          }
        }
      }

      @Override
      public void visitDec(Hir.Dec expr) {
        HirVisitor.super.visitDec(expr);
      }

      @Override
      public void visitFunctionSignature(Hir.FunctionSignature expr) {

        final var mirFnSignature = lowering.lower_function_signature_silent(expr);
        final var fnTy = new TyPointer<>(signatureToTy(mirFnSignature));
        final var createFnSignature = new Mir.InstrCreateFn(null, mirFnSignature, fnTy);
        createFnSignature.name(fnName == null ? null : new MirIdentifierId(fnName, null, 0));

        lowering.mirCtx.nodeStack().peek().instructions().add(createFnSignature);
        lowering.hirToMirMap.put(expr, createFnSignature);

        if (fnName != null) {
          lowering.mirCtx.scopeStack().peek().add(fnName, createFnSignature);
        }
      }

      @Override
      public void visitFunctionBody(Hir.Expression expr) {
        // Do not enter.
      }
    });

    lowering.lower(lowering.mirCtx.thirRaiseResult().root());

    final var processedNode = lowering.runPostProcessPasses(entryNode);

    return new MirLoweringResult(processedNode);
  }

  private <T extends MirNode> T runPostProcessPasses(T node) {

    MirNodeTyPass.startPass(node);
    MirFnDeclareMovePass.startPass(node);

    return node;
  }

  private Ty getTy(Hir.Expression expr) {
    return Objects.requireNonNull(expr.ty(), () -> "Could not find ty of expr '%s' (%s), fix in THIR stage".formatted(expr, expr.getClass().getSimpleName()));
  }

  private final Map<Hir.Expression, Mir.Instr> hirToMirMap = new HashMap<>();

  Mir.Instr lower(Hir.Expression expr) {

    final var existing = hirToMirMap.get(expr);
    if (existing != null) {
      return existing;
    }

    final Mir.Instr mir = switch (expr) {
      case Hir.Literal hir -> lower_literal(hir);
      case Hir.Return hir -> lower_return(hir);
      case Hir.BinaryOperation hir -> lower_binary_operation(hir);
      case Hir.Argument hir -> lower_argument(hir);
      case Hir.Call hir -> lower_call(hir);
      case Hir.Conditional hir -> lower_conditional(hir);
      case Hir.Block hir -> lower_block(hir);
      case Hir.Dec hir -> lower_variable_declaration(hir);
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

      default -> throw new NotImplementedException("Do not know how to handle '" + expr + "' (" + expr.getClass().getSimpleName() + ")");
    };

    hirToMirMap.put(expr, mir);
    return mir;
  }

  private Mir.Instr lower_path(Hir.Path it) {

    // The first path element must be accessible as any other base-level item, such as an identifier or type.
    final var elements = it.elements();
    return lower_path_elements(elements, 0, elements.length);
  }

  /**
   * Needs to be smarter, and be able to be more dynamic if the context is "set" rather than "get"
   */
  private Mir.Instr lower_path_elements(Hir.Expression[] elements, int start, int end) {

    var lastInstr = lower(elements[start]);

    for (var i = start + 1; i < end; i++) {
      lastInstr = getFieldAccessInstr(lastInstr, getTy(elements[i - 1]), elements[i]);
      mirCtx.nodeStack().peek().instructions().add(lastInstr);
    }

    return Objects.requireNonNull(lastInstr, "Path '" + Arrays.toString(elements) + "' could not be converted into an instruction");
  }

  @Nonnull
  private static Mir.Instr getFieldAccessInstr(Mir.Instr sourceInstr, Ty sourceTy, Hir.Expression memberExpr) {

    return switch (memberExpr) {
      case Hir.Lexeme lex -> switch (sourceTy) {
        case TyStruct struct -> {

          for (var n = 0; n < struct.fields().length; n++) {

            final var field = struct.fields()[n];
            if (field.name().equals(lex.name())) {
              yield new Mir.InstrGetStructElement(sourceInstr, n, field.ty());
            }
          }

          throw new IllegalArgumentException("Unknown field '" + lex + "'");
        }
        default -> throw new UnexpectedExpressionException(sourceTy);
      };
      default -> throw new UnexpectedExpressionException(memberExpr);
    };
  }

  private Mir.Instr lower_new_by_block(Hir.NewByBlock hir) {

    final var ty = getTy(hir);
    final Mir.Instr allocatorInstr = null; // lower(it.allocator()); // TODO: Lower allocator one day

    Mir.Instr[] arguments;
    switch (ty) {
      case TyStruct struct -> {

        arguments = new Mir.Instr[struct.fields().length];
        for (var i = 0; i < struct.fields().length; i++) {

          final var field = struct.fields()[i];
          final var assignment = Arrays.stream(hir.fields())
            .filter(f -> switch (f.lhs()) {
              case Hir.Identifier id -> field.name().equals(id.lexeme().name());
              case Hir.Lexeme lex -> field.name().equals(lex.name());
              default -> throw new UnexpectedExpressionException(f.lhs());
            })
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Could not find assignment for '" + field.name() + "'"));

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

  private Mir.Instr lower_struct(Hir.Struct hir) {

    final var ty = getTy(hir);
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

    final var givenTy = getTy(hir);
    final TyValueArray arrayTy;
    final Ty elementTy;
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

      final var literalSize = new Mir.InstrCreateLiteral(Objects.toString(arrayLength), Ty.INTEGER);
      mirCtx.nodeStack().peek().instructions().add(literalSize);
      lengthInstr = literalSize;
    } else {
      lengthInstr = lower(hir.length());
    }

    if (lengthInstr == null) {
      throw new IllegalArgumentException("There is no known size of '" + hir + "'");
    }

    final var arrayInstr = new Mir.InstrCreateArray(entries, lengthInstr, arrayTy, elementTy);

    mirCtx.nodeStack().peek().instructions().add(arrayInstr);

    return arrayInstr;
  }

  private Mir.Instr lower_array_access(Hir.ArrayAccess hir) {

    final var mirTarget = lower(hir.target());
    final var mirAccessor = lower(hir.accessor());

    final var resultTy = getTy(hir);

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
      final var identifierName = hirParameter.lexeme().name();

      final var parameterType = Objects.requireNonNullElseGet(
        getTy(hirParameters[i]),
        () -> expr_to_ty(hirParameter.valueType())
      );
      mirParameters[i] = new MirFnParameter(identifierName, parameterType);
    }

    final var returnType = Objects.requireNonNullElseGet(
      getTy(hirSignature.returnType()),
      () -> expr_to_ty(hirSignature.returnType())
    );

    // TODO: In the MIR stage we need to give a function a name? Or can we keep handling it as something not named?
    return new MirFnSignature(mirParameters, hirSignature.vararg(), returnType);
  }

  Mir.InstrCreateFn lower_function_signature(Hir.FunctionSignature hirSignature) {
    return Objects.requireNonNull((Mir.InstrCreateFn) hirToMirMap.get(hirSignature), ""); // externalFn;
  }

  private Mir.Instr lower_function(Hir.Function hir) {

    // TODO: Is this odd or correct? Maybe it is correct and it is up to MIR -> LLVM stage to move the function declaration to earlier?
    //        I guess that should only be made for "const" things -- and that will be how to differentiate between what you can refer to before declaration?
    //        Sounds like a good idea...

//    var mirFnSignature = lower_function_signature_silent();

    final var createFn = Objects.requireNonNull((Mir.InstrCreateFn) hirToMirMap.get(hir.signature()));
    final var mirFnSignature = createFn.signature();

    // fn name is extremely likely to be null.
    // It is up to some later pass to add a more descriptive name if possible.

    final var fnNode = new MirNode(null);

    final var offshoot = new ThirToMirLowering(this.mirCtx, mirCtx.machineTarget());
    offshoot.hirToMirMap.putAll(hirToMirMap);
    offshoot.mirCtx.nodeStack().add(fnNode);

    final var parent = mirCtx.scopeStack().peek();
    final var snapshot = parent.snapshot();

    final var fnScope = new MirScope(snapshot, "fn_root");
    offshoot.mirCtx.scopeStack().add(fnScope);

    // TODO: Need a good way to add parent scopes that are dynamic and make it easy to understand!

    try {

      // TODO: Remove fnStack, since all functions are flattened by now -- there can only be the one level
      offshoot.mirCtx.fnStack().add(mirFnSignature);
      final var bodyInstruction = offshoot.lower(hir.body());
    } finally {
      offshoot.mirCtx.fnStack().pop();
    }

    var processedNode = offshoot.runPostProcessPasses(fnNode);

//    if (mirFnSignature.returnType() == Ty.INFER) {

//      final var actualTy = switch (getTy(hir)) {
//        case TyFn it -> it.returnTy();
//        default -> throw new IllegalArgumentException("Cannot infer the result ty");
//      };

//      mirFnSignature = new MirFnSignature(
//        mirFnSignature.parameters(),
//        mirFnSignature.vararg(),
//        actualTy
//      );

//      final var original = processedNode;
//      processedNode = new MirNode(processedNode.name());
//      processedNode.instructions().addAll(original.instructions());
//      processedNode.successors().addAll(original.successors());
//    }

//    final var fnTy = signatureToTy(mirFnSignature);
//    final var fullFn = new Mir.InstrCreateFn(processedNode, mirFnSignature, new TyPointer<>(hir.ty()));

    createFn.entry(processedNode);

//    mirCtx.nodeStack().peek().instructions().add(fullFn);

    return createFn;
  }

  public static TyFn signatureToTy(MirFnSignature mirFnSignature) {
    final var paramTys = new TyParam[mirFnSignature.parameters().length];
    for (var i = 0; i < mirFnSignature.parameters().length; i++) {
      final var parameter = mirFnSignature.parameters()[i];
      paramTys[i] = new TyParam(parameter.name(), parameter.ty());
    }

    return new TyFn(paramTys, mirFnSignature.vararg(), mirFnSignature.returnType());
  }

  Ty expr_to_ty(Hir.Expression hir) {

    return switch (hir) {
      case Hir.TyExpr it -> it.ty();
      case Hir.Literal literal -> switch (literal.ty()) {
        case TyValueString _ -> Tys.fromString(literal.content(), mirCtx.machineTarget());
        default -> throw new IllegalArgumentException("Not valid literal '" + literal + "'");
      };
      default -> getTy(hir);
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
    node.instructions().add(jump);

    return null;
  }

  private Mir.Instr lower_loop_break(Hir.LoopBreak hir) {

    final var loop = mirCtx.loopStack().peek();
    final var node = mirCtx.nodeStack().peek();

    final var jump = new Mir.InstrJump(loop.exit());
    node.successors().add(loop.exit());
    node.instructions().add(jump);

    return null;
  }

  private Mir.Instr lower_identifier(Hir.Identifier hir) {

    final var identifierName = hir.lexeme().name();

    final var assignment = mirCtx.scopeStack().peek().get(identifierName);
    if (assignment != null) {
      return assignment;
    }

    final var paramInstr = getParamGetInstr(identifierName);
    if (paramInstr != null) {
      mirCtx.nodeStack().peek().instructions().add(paramInstr);
      return paramInstr;
    }

    throw new IllegalArgumentException("There is no variable '%s' found in scope".formatted(hir.lexeme()));
  }

  private Mir.InstrGetParam getParamGetInstr(String identifierName) {

    final var fnIterator = mirCtx.fnStack().descendingIterator();
    while (fnIterator.hasNext()) {

      final var fn = fnIterator.next();
      for (var i = 0; i < fn.parameters().length; i++) {
        final var param = fn.parameters()[i];
        if (Objects.equals(param.name(), identifierName)) {
          return new Mir.InstrGetParam(param);
        }
      }
    }

    return null;
  }

  private Mir.Instr lower_assignment(Hir.Assignment hir) {

    return switch (hir.lhs()) {
      case Hir.Dec lhs -> lower_assignment_root_level(hir, lhs.lexeme().name(), true);
      case Hir.Identifier lhs -> lower_assignment_root_level(hir, lhs.lexeme().name(), false);
      case Hir.Path lhs -> lower_assignment_to_path(hir, lhs);
      default -> throw new UnexpectedExpressionException(hir.lhs());
    };
  }

  /**
   * The target is not necessarily a struct, it can be anything really. Could be an export from another module.
   */
  private Mir.Instr lower_assignment_to_path(Hir.Assignment it, Hir.Path path) {

    // Convert the path into a get instruction, except for the last element which we write to.
    final var get_instruction = lower_path_elements(path.elements(), 0, path.elements().length - 1);

    // TODO: Implement!

    if (LLVMTys.isPointer(get_instruction.ty())) {

      final var innerTy = Tys.dereference(get_instruction.ty());
      final var lastElement = path.elements()[path.elements().length - 1];
      return switch (innerTy) {
        case TyStruct struct -> switch (lastElement) {
          case Hir.Identifier id -> lower_struct_field_assignment(it, struct, id.lexeme(), get_instruction);
          case Hir.Lexeme lex -> lower_struct_field_assignment(it, struct, lex, get_instruction);
          default -> throw new NotImplementedException();
        };
        default -> throw new UnexpectedExpressionException(innerTy);
      };
    }

    throw new NotImplementedException();
  }

  @Nonnull
  private Mir.InstrSetStructElement lower_struct_field_assignment(
    Hir.Assignment it,
    TyStruct struct,
    Hir.Lexeme lex,
    Mir.Instr get_instruction
  ) {

    for (var i = 0; i < struct.fields().length; i++) {

      final var field = struct.fields()[i];
      if (field.name().equals(lex.name())) {

        final var rhs = lower(it.rhs());
        final var instr = new Mir.InstrSetStructElement(get_instruction, i, rhs, field.ty());
        mirCtx.nodeStack().peek().instructions().add(instr);

        return instr;
      }
    }

    throw new IllegalArgumentException("Could not find field '" + lex + "' on '" + struct + "'");
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
    switch (rhs) {
      case Mir.InstrCreateArray ica -> instr = ica;
      case Mir.InstrStore is -> instr = is;
      case Mir.InstrCreateFn is -> {
        is.name(new MirIdentifierId(name, null, 0));
        instr = is;
      }
      case Mir.InstrCall is -> instr = is;
      case null, default -> {
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
    }

    // TODO: Should this actually be needed at all? Should it not be deduced some other way?
    scope.add(name, instr);

    return instr;
  }

  private Mir.Instr lower_variable_declaration(Hir.Dec hir) {

    log.debug("Variable declaration has no meaning in CFG, handle assignment expressions");
    return null;
  }

  @Data
  private static class PhiEntry {

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
        pass_node.successors().add(node_merge);
      }

      if (!fail_node.isTerminal()) {

        fail_node.instructions().add(new Mir.InstrJump(node_merge));
        fail_node.successors().add(node_merge);
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
          throw new IllegalArgumentException("Merged Phi node for '" + a + "' and '" + b + "' not usable");
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

    final var resultTy = Objects.requireNonNull(getTy(hir), "Conditional itself must have a result kind");

    // TODO: Need a "good" way of solving a phi with only one path because of conditional, until a Result(T) kind is added

    if (resultTy != Ty.VOID) {

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
    return lower(hir.children());
  }

  private Mir.Instr lower_argument(Hir.Argument hir) {
    return lower(hir.value());
  }

  private Mir.Instr lower_call(Hir.Call hir) {

    // TODO: This is very rudimentary -- it needs to be able to resolve the potential path into a struct, or array access, or whatever the heck
    Mir.Instr fnInstr;
    if (hir.target() instanceof Hir.Function fn) {

      fnInstr = lower_function(fn);

    } else {

      final var fnName = switch (hir.target()) {
        case Hir.Identifier identifier -> identifier.lexeme().name();
        case Hir.Lexeme lexeme -> lexeme.name();
        case Hir.Literal literal -> literal.content();
        default -> throw new UnexpectedExpressionException(hir.target());
      };

      // TODO: Add the function if it is not already registered
      //    It will be up to other code to realize the body if it ever arrives, otherwise it will be up to linked to give function definition.

      fnInstr = mirCtx.getInstructionByName(fnName);
    }

    final var mirFnArguments = new MirFnArgument[hir.arguments().length];
    Hir.Argument[] arguments = hir.arguments();
    for (int i = 0; i < arguments.length; i++) {

      final var argument_operand = lower(arguments[i]);
      Objects.requireNonNull(argument_operand, "Argument '" + arguments[i] + "' must produce an operand");

      final var argumentLabel = arguments[i].label();
      final var argumentLabelName = (argumentLabel == null) ? null : argumentLabel.name();

      mirFnArguments[i] = new MirFnArgument(argumentLabelName, argument_operand);
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
          "The target is a %s but did not lower to %s".formatted(
            Hir.Function.class.getSimpleName(),
            Mir.InstrCreateFn.class.getSimpleName()
          )
        );
      };
    }

    return lowerCallForTarget(fnInstr, mirFnArguments);
  }

  @Nonnull
  private Mir.Instr lowerCallForTarget(Mir.Instr fnInstr, MirFnArgument[] mirFnArguments) {

    return switch (fnInstr) {
      // NOTE: Unsure how this will be handled -- but I guess it will be an indirect function pointer?
      //        So it will be up to LLVM to decide what to do about it.
      case Mir.InstrStore store -> switch (store.value()) {
        case Mir.InstrReference ref -> lowerCallForTarget(ref.target(), mirFnArguments);
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
    final var ty = getTy(hir);
    final var instruction = new Mir.InstrBinaryOperation(lhs, mirBopKind, rhs, ty);
    mirCtx.nodeStack().peek().instructions().add(instruction);

    final var scope = mirCtx.scopeStack().peek();
    final var iid = scope.newUniqueId("bop");
    instruction.name(iid);

    return instruction;
  }

  private Mir.Instr lower_literal(Hir.Literal hir) {

    final var ty = Objects.requireNonNullElse(getTy(hir), hir.ty());
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
