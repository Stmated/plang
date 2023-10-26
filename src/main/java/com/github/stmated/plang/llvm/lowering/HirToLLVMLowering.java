package com.github.stmated.plang.llvm.lowering;

import static org.bytedeco.llvm.global.LLVM.LLVMAddIncoming;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildPhi;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

import com.github.stmated.plang.exceptions.GenericLLVMException;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnreachableCodeLLVMException;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

@Slf4j
public class HirToLLVMLowering {

  /**
   * a 'char *' used to retrieve error messages from LLVM
   */
  private final BytePointer error = new BytePointer();

  private final Map<String, LLVMTypeRef> typeCache = new HashMap<>();

  private final Stack<LLVMValueRef> fnStack = new Stack<>();
  private final Stack<LLVMBasicBlockRef> blockStack = new Stack<>();
  private final Stack<LLVMScope> scopeStack = new Stack<>();

  private final Map<String, LLVMValueRef> globalStringCache = new HashMap<>();
  private final Map<Byte, LLVMValueRef> cachedBytes = new HashMap<>();

  private LLVMContextRef context;
  private LLVMModuleRef module;
  private LLVMBuilderRef builder;

  private LLVMTypeRef printFnType;
  private LLVMValueRef printfFn;

  private LLVMTypeRef getType(String typeName) {

    final var cached = typeCache.get(typeName);
    if (cached != null) {
      return cached;
    }

    final var result = switch (typeName) {
      case "i32" -> LLVM.LLVMInt32TypeInContext(context);
      case "i8", "byte" -> LLVM.LLVMInt8TypeInContext(context);
      case "float", "float32" -> LLVM.LLVMFloatTypeInContext(context);
      case "float16" -> LLVM.LLVMHalfTypeInContext(context);
      case "double", "float64" -> LLVM.LLVMDoubleTypeInContext(context);
      default -> throw new IllegalArgumentException(STR."Unknown type name '\{typeName}'");
    };

    typeCache.put(typeName, result);
    return result;
  }

  private LLVMValueRef getGlobalStringPtr(String str) {
    return globalStringCache.computeIfAbsent(str, s -> LLVM.LLVMBuildGlobalStringPtr(builder, s, "str"));
  }

  private LLVMValueRef getByte(byte bite) {

    // Q: Is this worth it? Try with and without.
    final var charType = getType("byte");
    return cachedBytes.computeIfAbsent(bite, b -> LLVM.LLVMConstInt(charType, b, 0));
  }

  public Path lower_program(HirProgram program, Path output) throws IOException, InterruptedException {

    final String fileName;
    if (Files.isDirectory(output)) {
      fileName = "run";
    } else {
      fileName = output.getFileName().toString();
    }

    final var fileNameLastDotIndex = fileName.lastIndexOf('.');

    final String outputName;
    final String extension;
    if (fileNameLastDotIndex == -1) {
      outputName = fileName;
      extension = "";
    } else {
      outputName = fileName.substring(0, fileNameLastDotIndex);
      extension = fileName.substring(fileNameLastDotIndex);
    }

    // Stage 1: Initialize LLVM components
    LLVM.LLVMInitializeCore(LLVM.LLVMGetGlobalPassRegistry());
    LLVM.LLVMLinkInMCJIT();
    LLVM.LLVMInitializeNativeAsmPrinter();
    LLVM.LLVMInitializeNativeAsmParser();
    LLVM.LLVMInitializeNativeTarget();

    context = LLVM.LLVMContextCreate();
    module = LLVM.LLVMModuleCreateWithNameInContext(outputName, context);
    builder = LLVM.LLVMCreateBuilderInContext(context);

    // NOTE: Change this according to the actual target!
    LLVM.LLVMSetTarget(module, "arm64-apple-macosx14.0.0");

    // Stage 3: Verify the module using LLVMVerifier
    if (LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, error) != 0) {

      final var details = LLVM.LLVMPrintModuleToString(module).getString();

      final var errorMessage = error.getString();
      LLVM.LLVMDisposeMessage(error);

      throw map_error_message_to_exception(errorMessage, details);
    }

    // Stage 4: Create a pass pipeline using the legacy pass manager
    var pm = LLVM.LLVMCreatePassManager();
//    LLVM.LLVMAddAggressiveInstCombinerPass(pm);
//    LLVM.LLVMAddNewGVNPass(pm);
//    LLVM.LLVMAddCFGSimplificationPass(pm);
//    LLVM.LLVMRunPassManager(pm, module);

    // Stage 5: Execute the code using MCJIT
//    LLVMExecutionEngineRef engine = new LLVMExecutionEngineRef();
//    LLVMMCJITCompilerOptions options = new LLVMMCJITCompilerOptions();
//    if (LLVMCreateMCJITCompilerForModule(engine, module, options, 3, error) != 0) {
//      System.err.println("Failed to create JIT compiler: " + error.getString());
//      LLVMDisposeMessage(error);
//      return;
//    }

    try {

      scopeStack.push(new LLVMScope("global"));
      addMainFunction(program);
    } finally {
      scopeStack.pop();
    }

    // Stage 3: Verify the module using LLVMVerifier
    if (LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, error) != 0) {

      final var details = LLVM.LLVMPrintModuleToString(module).getString();

      final var errorString = error.getString();
      LLVM.LLVMDisposeMessage(error);

      throw map_error_message_to_exception(errorString, details);
    }

    if (log.isTraceEnabled()) {
      log.trace(LLVM.LLVMPrintModuleToString(module).getString());
    }

    final var directoryPath = output.toAbsolutePath().getParent();
    if (!Files.exists(directoryPath)) {
      Files.createDirectories(directoryPath);
    }

    final var bitcodePath = directoryPath.resolve(STR."\{outputName}.ll").toAbsolutePath();
    final var objectPath = directoryPath.resolve(STR."\{outputName}.o").toAbsolutePath();
    final var executablePath = directoryPath.resolve(STR."\{outputName}\{extension}").toAbsolutePath();

    LLVM.LLVMWriteBitcodeToFile(module, bitcodePath.toString());

    // Compile the .ll to .o using clang/llvm
    executeCommand(new String[]{"clang", "-c", bitcodePath.toString(), "-o", objectPath.toString()});
    executeCommand(new String[]{"clang", objectPath.toString(), "-o", executablePath.toString()});

    // Stage 6: Dispose of the allocated resources
    LLVM.LLVMDisposePassManager(pm);
    LLVM.LLVMDisposeBuilder(builder);
    LLVM.LLVMContextDispose(context);

    builder = null;
    module = null;
    context = null;

    printfFn = null;
    printFnType = null;

    if (Files.exists(executablePath)) {
      return executablePath.toAbsolutePath();
    } else {
      throw new IllegalArgumentException("Could not compile to an executable");
    }
  }

  private LLVMValueRef addMainFunction(HirProgram program) {

    // LLVM Types
    var i32Type = LLVM.LLVMInt32TypeInContext(context);
    var i8Type = LLVM.LLVMInt8TypeInContext(context);
    var i8PointerType = LLVM.LLVMPointerType(i8Type, 0);
    var i8PointerPointerType = LLVM.LLVMPointerType(i8PointerType, 0);
    var mainArgs = new LLVMTypeRef[]{i32Type, i8PointerPointerType};

    // Create main function
    var mainFn = LLVM.LLVMAddFunction(
      module, "main",
      LLVM.LLVMFunctionType(i32Type, new PointerPointer<>(mainArgs), mainArgs.length, 0)
    );
    var entryBlock = LLVM.LLVMAppendBasicBlockInContext(context, mainFn, "entry");
    LLVM.LLVMPositionBuilderAtEnd(builder, entryBlock);

    LLVMValueRef lastExpression;

    try {

      // TODO: This is wrong, the "mainFn" is not always in scope.
      fnStack.push(mainFn);
      try {

        // TODO: This is wrong, the "entryBlock" is not always in scope.
        blockStack.push(entryBlock);
        lastExpression = lower_expression(program);
      } finally {
        blockStack.pop();
      }
    } finally {
      fnStack.pop();
    }

    if (LLVM.LLVMIsATerminatorInst(lastExpression) == null) {

      dereferenceAndBuildRet(lastExpression);
//      LLVM.LLVMBuildRet(builder, lastExpression);
    }

    return mainFn;
  }

  private GenericLLVMException map_error_message_to_exception(String errorMessages, String details) {

    if (errorMessages != null && !errorMessages.isEmpty()) {

      if (containsAll(errorMessages, new String[]{"terminator", "found", "middle"})) {
        throw new UnreachableCodeLLVMException(errorMessages, details);
      }

    } else {
      errorMessages = "Unknown error";
    }

    return new GenericLLVMException(errorMessages, details);
  }

  private boolean containsAll(String haystack, String[] needles) {

    haystack = haystack.toLowerCase(Locale.ROOT);

    for (final var needle : needles) {
      if (!haystack.contains(needle)) {
        return false;
      }
    }

    return true;
  }

  private LLVMValueRef lower_expression(HirExpression hirExpression) {

    return switch (hirExpression) {
      default -> translate_expression(hirExpression);
    };
  }

  private LLVMValueRef lower_llvm_call(HirCall hir) {

    final var functionName = hir.functionReference().function().identifier().name();

    LLVMTypeRef fnType;
    LLVMValueRef fn;
    if ("printf".equals(functionName)) {

      if (printfFn == null) {

        var i8PointerType = LLVM.LLVMPointerType(getType("i8"), 0);
        LLVMTypeRef[] printfArgs = {i8PointerType};
        printFnType = LLVM.LLVMFunctionType(getType("i32"), new PointerPointer<>(printfArgs), printfArgs.length, 1);
        printfFn = LLVM.LLVMAddFunction(module, "printf", printFnType);
      }

      fnType = printFnType;
      fn = printfFn;

    } else {
      throw new IllegalArgumentException(STR."Unknown function '\{functionName}'");
    }

    final var llvmArgs = new LLVMValueRef[hir.arguments().length];
    for (var i = 0; i < hir.arguments().length; i++) {
      llvmArgs[i] = translate_expression(hir.arguments()[i]);
    }

//    var format = LLVMBuildGlobalStringPtr(builder, "%ld\n", "format");

    return LLVM.LLVMBuildCall2(
      builder, fnType, fn, new PointerPointer<>(llvmArgs), llvmArgs.length, STR."call \{functionName}"
    );
  }

  private LLVMValueRef lower_llvm_return(HirReturn hir) {

    final var expr = translate_expression(hir.expression());
    return dereferenceAndBuildRet(expr);
  }

  /**
   * Dereferences the given expression and builds a return statement.
   * <p>
   * TODO: Is most likely incorrect, since we might actually *want* to return the pointer and not the underlying value...
   *
   * @param expr the expression to dereference
   * @return the return statement
   */
  private LLVMValueRef dereferenceAndBuildRet(LLVMValueRef expr) {

    final var type = LLVM.LLVMTypeOf(expr);
    if (LLVM.LLVMGetTypeKind(type) == LLVM.LLVMPointerTypeKind) {

      // TODO: NEED TO KEEP TRACK OF THE TYPE! NEED TO TRUST WHAT THE HIR IS TELLING US -- BLINDLY!
      final var loaded = LLVM.LLVMBuildLoad2(builder, getType("i32"), expr, "loadedA");

      return LLVM.LLVMBuildRet(builder, loaded);

    } else {

      return LLVM.LLVMBuildRet(builder, expr);
    }
  }

  private LLVMValueRef load_or_reuse(HirExpression hir) {

    return switch (hir) {
      case HirIdentifier identifier -> {
        final var details = lower_identifier_detailed(identifier);
        final var preLoaded = details.loaded().get();
        if (preLoaded != null) {
          yield preLoaded;
        }

        // TODO: This should be known and trusted from the HIR nodes, and NOT investigated through LLVM
        // TODO: WE MUST KEEP TRACK OF THIS OURSELVES! SINCE LLVM ONLY KNOWS OF IT AS A POINTER!
        final var lhsType = getType("i32"); //LLVM.LLVMTypeOf(lhs);

        final var loaded = LLVM.LLVMIsConstant(details.ref()) == 1
          ? details.ref()
          : LLVM.LLVMBuildLoad2(builder, lhsType, details.ref(), hir.toString());

        details.loaded().set(loaded);

        yield loaded;
      }
      default -> {

        final var translated = translate_expression(hir);

        // TODO: This should be known and trusted from the HIR nodes, and NOT investigated through LLVM
        // TODO: WE MUST KEEP TRACK OF THIS OURSELVES! SINCE LLVM ONLY KNOWS OF IT AS A POINTER!
        final var lhsType = getType("i32"); //LLVM.LLVMTypeOf(lhs);

        final var loaded = LLVM.LLVMIsConstant(translated) == 1
          ? translated
          : LLVM.LLVMBuildLoad2(builder, lhsType, translated, hir.toString());

        yield loaded;
      }
    };
  }

  private LLVMValueRef lower_binary_operation(HirBinaryOperation hir) {

    final var lhs = load_or_reuse(hir.lhs());
    final var rhs = load_or_reuse(hir.rhs());

    // TODO: This should be known and trusted from the HIR nodes, and NOT investigated through LLVM
    // TODO: WE MUST KEEP TRACK OF THIS OURSELVES! SINCE LLVM ONLY KNOWS OF IT AS A POINTER!
    final var lhsType = getType("i32"); //LLVM.LLVMTypeOf(lhs);
    final var rhsType = getType("i32"); //LLVM.LLVMTypeOf(rhs);

    // TODO: Store this somewhere in the HIR, and trust it, and do not investigate like this.
//    final var loadedLhs = LLVM.LLVMIsConstant(lhs) == 1 ? lhs : LLVM.LLVMBuildLoad2(builder, lhsType, lhs, hir.lhs().toString());
//    final var loadedRhs = LLVM.LLVMIsConstant(rhs) == 1 ? rhs : LLVM.LLVMBuildLoad2(builder, rhsType, rhs, hir.rhs().toString());

    return switch (hir.type()) {
      case ADD -> LLVM.LLVMBuildAdd(builder, lhs, rhs, STR."\{hir.lhs()} + \{hir.rhs()}");
      case SUBTRACT -> LLVM.LLVMBuildSub(builder, lhs, rhs, STR."\{hir.lhs()} - \{hir.rhs()}");
      case MULTIPLY -> LLVM.LLVMBuildMul(builder, lhs, rhs, STR."\{hir.lhs()} * \{hir.rhs()}");
      case DIVIDE -> {

        final var lhsKind = LLVM.LLVMGetTypeKind(lhsType);
        final var rhsKind = LLVM.LLVMGetTypeKind(rhsType);

        if (lhsKind == LLVM.LLVMIntegerTypeKind && rhsKind == LLVM.LLVMIntegerTypeKind) {

          // TODO: Need to have information in the HIR for if these values are signed or unsigned.
          yield LLVM.LLVMBuildSDiv(builder, lhs, rhs, "lhs / rhs");
        }

        // TODO: This is wrong -- if it is two float16 it should not be converted into a float32
        LLVMValueRef correctLhs;
        LLVMValueRef correctRhs;
        if (lhsKind == LLVM.LLVMDoubleTypeKind || rhsKind == LLVM.LLVMDoubleTypeKind) {
          correctLhs = getValueAsFloat(lhs, getType("float64"), lhsKind, hir.lhs());
          correctRhs = getValueAsFloat(rhs, getType("float64"), rhsKind, hir.rhs());
        } else if (lhsKind == LLVM.LLVMFloatTypeKind || rhsKind == LLVM.LLVMFloatTypeKind) {
          correctLhs = getValueAsFloat(lhs, getType("float32"), lhsKind, hir.lhs());
          correctRhs = getValueAsFloat(rhs, getType("float32"), rhsKind, hir.rhs());
        } else if (lhsKind == LLVM.LLVMHalfTypeKind || rhsKind == LLVM.LLVMHalfTypeKind) {
          correctLhs = getValueAsFloat(lhs, getType("float16"), lhsKind, hir.lhs());
          correctRhs = getValueAsFloat(rhs, getType("float16"), rhsKind, hir.rhs());
        } else {
          throw new NotImplementedException(STR."Cannot divide values of types '\{hir.lhs()}' and '\{hir.rhs()}'");
        }

        yield LLVM.LLVMBuildFDiv(builder, correctLhs, correctRhs, STR."\{hir.lhs()} / \{hir.rhs()}");
      }
      case EQUALS -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, lhs, rhs, STR."\{hir.lhs()} == \{hir.rhs()}");
      case LT -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSLT, lhs, rhs, STR."\{hir.lhs()} < \{hir.rhs()}");
      case LTE -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSLE, lhs, rhs, STR."\{hir.lhs()} <= \{hir.rhs()}");
      case GT -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSGT, lhs, rhs, STR."\{hir.lhs()} > \{hir.rhs()}");
      case GTE -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSGE, lhs, rhs, STR."\{hir.lhs()} >= \{hir.rhs()}");
      default -> throw new IllegalArgumentException(STR."Unknown binary operation kind '\{hir.type()}'");
    };
  }

  private LLVMValueRef getValueAsFloat(LLVMValueRef v, LLVMTypeRef targetType, int lhsKind, HirExpression hir) {

    if (lhsKind == LLVM.LLVMFloatTypeKind) {
      return v;
    } else if (lhsKind == LLVM.LLVMHalfTypeKind) {
      return LLVM.LLVMBuildFPExt(builder, v, targetType, "float16ToFloat32");
    } else if (lhsKind == LLVM.LLVMDoubleTypeKind) {
      return LLVM.LLVMBuildFPTrunc(builder, v, targetType, "doubleToFloat32");
    } else if (lhsKind == LLVM.LLVMIntegerTypeKind) {
      return LLVM.LLVMBuildSIToFP(builder, v, targetType, "intToFloat32");
    } else {
      throw new NotImplementedException(STR."Cannot convert '\{hir}' into a float");
    }
  }

  private LLVMValueRef lower_expressions(HirExpression[] expressions) {

    LLVMValueRef lastRef = null;
    for (final var expr : expressions) {
      lastRef = translate_expression(expr);
    }

    return lastRef;
  }

  private LLVMValueRef translate_expression(HirExpression hirExpression) {
    return translate_expression(hirExpression, false);
  }

  private LLVMValueRef translate_expression(HirExpression hirExpression, boolean dynamic) {

    return switch (hirExpression) {
      case HirLiteral hir -> lower_literal(hir, dynamic);
      case HirBinaryOperation hir -> lower_binary_operation(hir);
      case HirArgument hir -> lower_llvm_argument(hir);
      case HirCall hir -> lower_llvm_call(hir);
      case HirReturn hir -> lower_llvm_return(hir);
      case HirConditional hir -> lower_conditional(hir);
      case HirBlock hir -> lower_block(hir);
      case HirAssignment hir -> lower_assignment(hir);
      case HirIdentifier hir -> lower_identifier(hir);
      case HirLoop hir -> lower_loop(hir);
      case HirLoopBreak hir -> lower_loop_break(hir);
      case HirLoopContinue hir -> lower_loop_continue(hir);
      case HirExpressionCollection hir -> lower_expressions(hir.children());
      case HirProgram hir -> lower_expressions(hir.expressions());
      default -> throw new NotImplementedException(STR."Unknown expression '\{hirExpression.getClass().getSimpleName()}'");
    };
  }

  private record LoopScope(LLVMBasicBlockRef next, LLVMBasicBlockRef exit) {

  }

  private Stack<LoopScope> loopStack = new Stack<>();

  private LLVMValueRef lower_loop(HirLoop hir) {

    final var fn = fnStack.peek();

    LLVMBasicBlockRef loop = LLVM.LLVMAppendBasicBlockInContext(context, fn, "loop");
    LLVMBasicBlockRef after_loop = LLVM.LLVMAppendBasicBlockInContext(context, fn, "after_loop");

    // Jump to the loop block
    LLVM.LLVMBuildBr(builder, loop);

    // Start insertion in loop block
    LLVMPositionBuilderAtEnd(builder, loop);

    try {

      // The result of the loop is the body of the loop.
      // TODO: This will need some seriously big revisions once I figure out all the requirements/intricacies.
      loopStack.push(new LoopScope(loop, after_loop));
      try {
        blockStack.push(loop);
        return translate_expression(hir.body());
      } finally {
        blockStack.pop();
      }
    } finally {
      loopStack.pop();
      LLVMPositionBuilderAtEnd(builder, after_loop);
    }
  }

  private LLVMValueRef lower_loop_break(HirLoopBreak hir) {

    // TODO: This could likely be improved by converting the conditional into a conditional branch if it is either-or.
    final var loop = loopStack.peek();
    return LLVM.LLVMBuildBr(builder, loop.exit());
  }

  private LLVMValueRef lower_loop_continue(HirLoopContinue hir) {

    // TODO: This could likely be improved by converting the conditional into a conditional branch if it is either-or.
    final var loop = loopStack.peek();
    return LLVM.LLVMBuildBr(builder, loop.next());
  }

  private LLVMScopeValue lower_identifier_detailed(HirIdentifier hir) {
    final var identifierName = hir.name();

    for (var i = scopeStack.size() - 1; i >= 0; i--) {

      final var scope = scopeStack.get(i);
      final var var = scope.map().get(identifierName);
      if (var != null) {
        return var;
      }
    }

    throw new IllegalArgumentException(STR."There is no variable '\{hir.name()}' found in scope");
  }

  private LLVMValueRef lower_identifier(HirIdentifier hir) {
    return lower_identifier_detailed(hir).ref();
  }

  private LLVMValueRef lower_assignment(HirAssignment hirAssignment) {

    return switch (hirAssignment.lhs()) {
      case HirVariableDeclaration lhs -> {

        final var targetName = lhs.identifier().name();
        final var rhs = translate_expression(hirAssignment.rhs(), true);

        final var scope = scopeStack.peek();

        for (var scopeIndex = scopeStack.size() - 1; scopeIndex >= 0; scopeIndex--) {
          if (scopeStack.get(scopeIndex).map().containsKey(targetName)) {
            throw new IllegalArgumentException(STR."Not allowed to re-declare '\{targetName}'");
          }
        }

        scope.map().put(targetName, new LLVMScopeValue(rhs));

        yield rhs;
      }
      case HirIdentifier lhs -> {

        // TODO: If "ptr" is a constant (change dynamic=false above for declaration)
        //        Then we need to Alloca the result of rhs and place that allocation inside lhs
        //        Also, if rhs is a constant, then we can just replace the scoped variable with that constant
        //        This will need A LOT of work, but can be saved for another time for later optimizations.
        final var ptr_detailed = lower_identifier_detailed(lhs);

        // Replace the value in the scope.
        // TODO: This might need changing later, since it deals with altering value only in current scope.
        ptr_detailed.loaded().set(null); // Clear the loaded, letting it re-load on the next use-site.

        if (hirAssignment.rhs() instanceof HirIdentifier rhs_identifier) {

          final var scope = scopeStack.peek();
          final var rhs_detailed = lower_identifier_detailed(rhs_identifier);
          scope.map().put(lhs.name(), rhs_detailed);

          // Q: Is this correct? Should an assignment return the value, or should it return some sort of Option/Result that always fails?
          yield rhs_detailed.ref();

        } else {

          final var val = translate_expression(hirAssignment.rhs(), false);
          LLVM.LLVMBuildStore(builder, val, ptr_detailed.ref());

          // Q: Is this correct? Should an assignment return the value, or should it return some sort of Option/Result that always fails?
          yield val;
        }
      }
      default -> throw new NotImplementedException(STR."Cannot handle '\{hirAssignment.lhs()}' for lhs assignment");
    };
  }

  private LLVMValueRef lower_conditional(HirConditional hir) {

    final var parent_block = blockStack.peek();
    final var fn = fnStack.peek();

    final var block_true = LLVMAppendBasicBlockInContext(context, fn, "conditional_block_true");

    LLVMValueRef block_true_last;
    LLVMValueRef block_false_last;

    try {
      blockStack.push(block_true);
      LLVMPositionBuilderAtEnd(builder, block_true);
      block_true_last = translate_expression(hir.pass());
    } finally {
      blockStack.pop();
    }

    final var block_false = LLVMAppendBasicBlockInContext(context, fn, "conditional_block_false");

    try {
      blockStack.push(block_false);
      LLVMPositionBuilderAtEnd(builder, block_false);
      block_false_last = translate_expression(hir.fail());
    } finally {
      blockStack.pop();
    }

    LLVMPositionBuilderAtEnd(builder, block_false);

    // TODO: Needs to add support for the "phi" way of adding to a "result"
    //        Then that "result" ref needs to be returned, since ALL expressions should have a result

    final var block_true_terminal = LLVM.LLVMGetBasicBlockTerminator(block_true) != null;
    final var block_false_terminal = LLVM.LLVMGetBasicBlockTerminator(block_false) != null;

    LLVMBasicBlockRef block_merge = null;
    if (!block_true_terminal || !block_false_terminal) {

      LLVMPositionBuilderAtEnd(builder, parent_block);
      block_merge = LLVMAppendBasicBlockInContext(context, fn, "conditional_merge");

      if (!block_true_terminal) {

        // There is no terminator in the block, so we will need to branch to the merge block.
        LLVMPositionBuilderAtEnd(builder, block_true);
        LLVMBuildBr(builder, block_merge);
      }

      if (!block_false_terminal) {

        // There is no terminator in the block, so we will need to branch to the merge block.
        LLVMPositionBuilderAtEnd(builder, block_false);
        LLVMBuildBr(builder, block_merge);
      }
    }

    LLVMPositionBuilderAtEnd(builder, parent_block);
    final var condition = translate_expression(hir.predicate());
    final var branch = LLVMBuildCondBr(builder, condition, block_true, block_false);

    if (block_merge != null) {

      if (!block_true_terminal && !block_false_terminal) {

        // Both paths are non-terminal, so we will use a phi node to decide the result and give that back.
        LLVMPositionBuilderAtEnd(builder, block_merge);

        final var true_type = LLVM.LLVMTypeOf(block_true_last);
        final var false_type = LLVM.LLVMTypeOf(block_false_last);
        final var common_type = get_common_type(true_type, false_type);

        LLVMValueRef phi = LLVMBuildPhi(builder, common_type, "result");
        PointerPointer<Pointer> phiValues = new PointerPointer<>(2)
          .put(0, block_true_last)
          .put(1, block_false_last);
        PointerPointer<Pointer> phiBlocks = new PointerPointer<>(2)
          .put(0, block_true)
          .put(1, block_false);
        LLVMAddIncoming(phi, phiValues, phiBlocks, 2);

        return phi;
      } else if (!block_true_terminal) {

        // Only the true block is not terminal, so just return that.
//        LLVMPositionBuilderAtEnd(builder, block_merge);
        return block_true_last;

      } else {

        // Only the false block is not terminal, so just return that.
        LLVMPositionBuilderAtEnd(builder, block_merge);
        return block_false_last;
      }

    } else {

      // Both paths are terminal. So we will simply return the branch itself.
      return branch;
    }
  }

  private LLVMTypeRef get_common_type(LLVMTypeRef a, LLVMTypeRef b) {

    if (a.equals(b)) {
      return a;
    }

    throw new IllegalArgumentException(STR."Types '\{a}' and '\{b}' are not equal. No type conversion yet");
  }

  private LLVMTypeRef get_common_type(HirExpression a, HirExpression b) {
    return null;
  }

  private LLVMValueRef lower_block(HirBlock hir) {

    final var parent_block = blockStack.peek();
    final var fn = fnStack.peek();

    final var block = LLVMAppendBasicBlockInContext(context, fn, "block");

    // Branch to this block
    LLVMPositionBuilderAtEnd(builder, parent_block);
    LLVMBuildBr(builder, block);

    try {

      blockStack.push(block);
      LLVMValueRef last = null;
      for (final var child : hir.children()) {

        LLVMPositionBuilderAtEnd(builder, block);
        last = translate_expression(child);
      }

      return last;

    } finally {
      blockStack.pop();
    }
  }

  private LLVMValueRef lower_llvm_argument(HirArgument hir) {
    return translate_expression(hir.expression());
  }

  private LLVMValueRef lower_literal(HirLiteral hirLiteral, boolean dynamic) {

    if (hirLiteral.literal() == null) {

      // TODO: This needs to refer to the correct type, since null can be of different types.
      // TODO: Then later, null needs to not exist at all.
      return LLVM.LLVMConstNull(getType("i32"));
    }

    // TODO: Figure out a way to know better when to use constants and when to use allocated values

    if (hirLiteral.literal() instanceof String) {

      final var str = (String) hirLiteral.literal();

      if (dynamic) {

        // \0-terminate the string and get it as utf-8 bytes.
        final var bytes = (STR."\{str}\0").getBytes(StandardCharsets.UTF_8);
        final var charArray = new LLVMValueRef[bytes.length];
        final var charType = LLVM.LLVMInt8TypeInContext(context);
        for (int i = 0; i < bytes.length; i++) {
          charArray[i] = getByte(bytes[i]);
        }

        final var strArray = LLVM.LLVMConstArray(charType, new PointerPointer<>(charArray), bytes.length);

        final var charArrayType = LLVM.LLVMArrayType(charType, bytes.length);
        final var globalVar = LLVM.LLVMAddGlobal(module, charArrayType, STR."globalString: \{str}");
        LLVM.LLVMSetInitializer(globalVar, strArray);

      } else {

        return getGlobalStringPtr(str);
      }
    }

    final var constant = switch (hirLiteral.literal()) {
      case Integer v -> LLVM.LLVMConstInt(getType("i32"), v, 0);
      case Double v -> LLVM.LLVMConstReal(getType("double"), v);
      case Float v -> LLVM.LLVMConstReal(getType("float32"), v);
      default -> throw new IllegalArgumentException(STR."Unknown literal '\{hirLiteral.literal()}'");
    };

    if (dynamic) {

      final var allocation = switch (hirLiteral.literal()) {
        case Integer _ -> LLVM.LLVMBuildAlloca(builder, getType("i32"), "int");
        case Double _ -> LLVM.LLVMBuildAlloca(builder, getType("double"), "double");
        case Float _ -> LLVM.LLVMBuildAlloca(builder, getType("float32"), "float32");
        default -> throw new IllegalArgumentException(STR."Unknown literal '\{hirLiteral.literal()}'");
      };

      LLVM.LLVMBuildStore(builder, constant, allocation);
      return allocation;

    } else {
      return constant;
    }
  }

  private static void executeCommand(String[] commandParts) throws IOException, InterruptedException {

    var p = Runtime.getRuntime().exec(commandParts);
    var errorLines = new ArrayList<String>();
    try (var error = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
      String line;
      while ((line = error.readLine()) != null) {
        errorLines.add(line);
      }
    }

    if (!errorLines.isEmpty()) {
      throw new IllegalArgumentException(String.join("\n", errorLines));
    }

    p.waitFor();
  }
}
