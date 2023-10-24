package com.github.stmated.plang.llvm.lowering;

import static org.bytedeco.llvm.global.LLVM.LLVMAddIncoming;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildPhi;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

import com.github.stmated.plang.exceptions.GenericLLVMException;
import com.github.stmated.plang.exceptions.UncaughtLLVMException;
import com.github.stmated.plang.exceptions.UnreachableCodeLLVMException;
import com.github.stmated.plang.hir.model.HirArgument;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirBlock;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirConditional;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;
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
      case "i8" -> LLVM.LLVMInt8TypeInContext(context);
      default -> throw new IllegalArgumentException(STR."Unknown type name '\{typeName}'");
    };

    typeCache.put(typeName, result);
    return result;
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
    if (LLVM.LLVMVerifyModule(module, LLVM.LLVMPrintMessageAction, error) != 0) {
      LLVM.LLVMDisposeMessage(error);
      throw new IllegalArgumentException(error.getString());
    }

    // Stage 4: Create a pass pipeline using the legacy pass manager
    var pm = LLVM.LLVMCreatePassManager();
//    LLVM.LLVMAddAggressiveInstCombinerPass(pm);
    LLVM.LLVMAddNewGVNPass(pm);
    LLVM.LLVMAddCFGSimplificationPass(pm);
    LLVM.LLVMRunPassManager(pm, module);

    // Stage 5: Execute the code using MCJIT
//    LLVMExecutionEngineRef engine = new LLVMExecutionEngineRef();
//    LLVMMCJITCompilerOptions options = new LLVMMCJITCompilerOptions();
//    if (LLVMCreateMCJITCompilerForModule(engine, module, options, 3, error) != 0) {
//      System.err.println("Failed to create JIT compiler: " + error.getString());
//      LLVMDisposeMessage(error);
//      return;
//    }

    addMainFunction(program);

    // Save the IR to a file.

    final var directoryPath = output.toAbsolutePath().getParent();
    if (!Files.exists(directoryPath)) {
      Files.createDirectories(directoryPath);
    }

    final var bitcodePath = directoryPath.resolve(STR."\{outputName}.ll").toAbsolutePath();
    final var objectPath = directoryPath.resolve(STR."\{outputName}.o").toAbsolutePath();
    final var executablePath = directoryPath.resolve(STR."\{outputName}\{extension}").toAbsolutePath();

    LLVM.LLVMWriteBitcodeToFile(module, bitcodePath.toString());

    LLVM.LLVMDumpModule(module);

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

  private void addMainFunction(HirProgram program) {

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

    // TODO: Add the entry code here
//
//    // [OPTIONAL] Create factorial argument
//    LLVMValueRef factorialFn = LLVMGetNamedFunction(module, "factorial");
//
//    LLVMTypeRef factorialType = LLVMFunctionType(i32Type, i32Type, 1, 0);
//
//    var arguments = new PointerPointer<>(1)
//      .put(0, LLVMConstInt(i32Type, 30, 0));
//
//    LLVMValueRef factorialCall = LLVMBuildCall2(
//      builder,
//      factorialType,
//      factorialFn,
//      arguments, 1, "factorialtmp"
//    );
//
//    LLVMTypeRef[] printfArgs = {i8PointerType};
//    var printFnType = LLVMFunctionType(i32Type, new PointerPointer<>(printfArgs), printfArgs.length, 1);
//    LLVMValueRef printfFn = LLVMAddFunction(module, "printf", printFnType);
//
//    var format = LLVMBuildGlobalStringPtr(builder, "%ld\n", "format");
//    LLVMBuildCall2(builder, printFnType, printfFn, new PointerPointer<>(format, factorialCall), 2, "printfCall");

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

//      LLVMPositionBuilderAtEnd(builder, entryBlock);
      LLVM.LLVMBuildRet(builder, lastExpression);
    }

    final var exception = captureError(() -> LLVM.LLVMVerifyFunction(mainFn, LLVM.LLVMPrintMessageAction));

    if (exception != null) {
      throw exception;
    }
  }

  private RuntimeException captureError(Supplier<Integer> runnable) {

    final var originalErrStream = System.err;
    final var originalOutStream = System.out;

    try {

      // Create a custom output stream to capture the messages
      final var baosErr = new ByteArrayOutputStream();
      final var baosOut = new ByteArrayOutputStream();
      System.setErr(new PrintStream(baosErr));
      System.setOut(new PrintStream(baosOut));

      if (runnable.get() != 0) {

        var errorMessages = baosErr.toString();
        if (errorMessages != null && errorMessages.length() > 0) {

          if (containsAll(errorMessages, new String[] {"terminator", "found", "middle"})) {
            throw new UnreachableCodeLLVMException(errorMessages);
          }

        } else {
          errorMessages = "Unknown error";
        }

        return new GenericLLVMException(errorMessages);
      }

      return null;

    } finally {

      // Restore original error stream
      System.setErr(originalErrStream);
      System.setOut(originalOutStream);
    }
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
      case HirProgram hir -> {
        LLVMValueRef lastRef = null;
        for (final var expr : hir.expressions()) {
          lastRef = translate_expression(expr);
        }

        yield lastRef;
      }
//      case HirBinaryOperation hir -> lower_binary_operation(module, builder, hir);
//      case HirNoOp hir -> {
//        // Do nothing
//      }
      default -> translate_expression(hirExpression);

      //throw new IllegalArgumentException(STR."Unknown expression '\{hirExpression.getClass().getSimpleName()}'");
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
    return LLVM.LLVMBuildRet(builder, expr);
  }

  private LLVMValueRef lower_binary_operation(HirBinaryOperation hir) {

    final var lhs = translate_expression(hir.lhs());
    final var rhs = translate_expression(hir.rhs());

    return switch (hir.type()) {
      case ADD -> LLVM.LLVMBuildAdd(builder, lhs, rhs, "lhs + rhs");
      case SUBTRACT -> LLVM.LLVMBuildSub(builder, lhs, rhs, "lhs - rhs");
      case MULTIPLY -> LLVM.LLVMBuildMul(builder, lhs, rhs, "lhs * rhs");
      case DIVIDE -> LLVM.LLVMBuildFDiv(builder, lhs, rhs, "lhs / rhs");
      case EQUALS -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, lhs, rhs, "lhs == rhs");
      case LT -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSLT, lhs, rhs, "lhs < rhs");
      case LTE -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSLE, lhs, rhs, "lhs <= rhs");
      case GT -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSGT, lhs, rhs, "lhs > rhs");
      case GTE -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSGE, lhs, rhs, "lhs >= rhs");
      default -> throw new IllegalArgumentException(STR."Unknown binary operation kind '\{hir.type()}'");
    };
  }

  private LLVMValueRef translate_expression(HirExpression hirExpression) {

    return switch (hirExpression) {
      case HirLiteral hir -> translate_literal(hir);
      case HirBinaryOperation hir -> lower_binary_operation(hir);
      case HirArgument hir -> lower_llvm_argument(hir);
      case HirCall hir -> lower_llvm_call(hir);
      case HirReturn hir -> lower_llvm_return(hir);
      case HirConditional hir -> lower_conditional(hir);
      case HirBlock hir -> lower_block(hir);
      default ->
        throw new IllegalArgumentException(STR."Unknown expression '\{hirExpression.getClass().getSimpleName()}'");
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

    final var block_true_non_terminal = block_true != null && LLVM.LLVMGetBasicBlockTerminator(block_true) == null;
    final var block_false_non_terminal = block_false != null && LLVM.LLVMGetBasicBlockTerminator(block_false) == null;

    LLVMBasicBlockRef block_merge = null;
    if (block_true_non_terminal || block_false_non_terminal) {

      LLVMPositionBuilderAtEnd(builder, parent_block);
      block_merge = LLVMAppendBasicBlockInContext(context, fn, "conditional_merge");

      if (block_true_non_terminal) {

        // There is no terminator in the block, so we will need to branch to the merge block.
        LLVMPositionBuilderAtEnd(builder, block_true);
        LLVMBuildBr(builder, block_merge);
      }

      if (block_false_non_terminal) {

        // There is no terminator in the block, so we will need to branch to the merge block.
        LLVMPositionBuilderAtEnd(builder, block_false);
        LLVMBuildBr(builder, block_merge);
      }
    }

    LLVMPositionBuilderAtEnd(builder, parent_block);
    final var condition = translate_expression(hir.predicate());
    LLVMBuildCondBr(builder, condition, block_true, block_false);

    if (block_merge != null) {

      if (block_true_non_terminal && block_false_non_terminal) {

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
      } else if (block_true_non_terminal) {

        // Only the true block is not terminal, so just return that.
        return block_true_last;

      } else {

        // Only the false block is not terminal, so just return that.
        LLVMPositionBuilderAtEnd(builder, block_merge);
        return block_false_last;
      }

    } else {

      // Both paths are terminal. So we will simply return the condition branch.
      return condition;
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

  private LLVMValueRef translate_literal(HirLiteral hirLiteral) {

    if (hirLiteral.literal() == null) {
      return LLVM.LLVMConstNull(getType("i32"));
    }

    return switch (hirLiteral.literal()) {
      case Integer v -> LLVM.LLVMConstInt(getType("i32"), v, 0);
      case String v -> LLVM.LLVMBuildGlobalStringPtr(builder, v, v);
      default -> throw new IllegalArgumentException(STR."Unknown literal '\{hirLiteral.literal()}'");
    };
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
