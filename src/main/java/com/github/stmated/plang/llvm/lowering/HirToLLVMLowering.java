package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.hir.model.HirArgument;
import com.github.stmated.plang.hir.model.HirBinaryOperation;
import com.github.stmated.plang.hir.model.HirCall;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirLiteral;
import com.github.stmated.plang.hir.model.HirNoOp;
import com.github.stmated.plang.hir.model.HirProgram;
import com.github.stmated.plang.hir.model.HirReturn;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

@Slf4j
public class HirToLLVMLowering {

  /**
   * a 'char *' used to retrieve error messages from LLVM
   */
  private static final BytePointer error = new BytePointer();

  private static final Map<String, LLVMTypeRef> typeCache = new HashMap<>();

  private static LLVMTypeRef getType(String typeName) {

    final var cached = typeCache.get(typeName);
    if (cached != null) {
      return cached;
    }

    final var result = switch (typeName) {
      case "i32" -> LLVM.LLVMInt32Type();
      case "i8" -> LLVM.LLVMInt8Type();
      default -> throw new IllegalArgumentException(STR."Unknown type name '\{typeName}'");
    };

    typeCache.put(typeName, result);
    return result;
  }

  public static Path lower_program(HirProgram program, Path output) throws IOException, InterruptedException {

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

    // Stage 2: Build the factorial function.
    var context = LLVM.LLVMContextCreate();
    var module = LLVM.LLVMModuleCreateWithNameInContext(outputName, context);
    var builder = LLVM.LLVMCreateBuilderInContext(context);

    // NOTE: Change this according to the actual target!
    LLVM.LLVMSetTarget(module, "arm64-apple-macosx14.0.0");

    // Stage 3: Verify the module using LLVMVerifier
    if (LLVM.LLVMVerifyModule(module, LLVM.LLVMPrintMessageAction, error) != 0) {
      LLVM.LLVMDisposeMessage(error);
      throw new IllegalArgumentException(error.getString());
    }

    // Stage 4: Create a pass pipeline using the legacy pass manager
    var pm = LLVM.LLVMCreatePassManager();
//    LLVMAddAggressiveInstCombinerPass(pm);
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


    addMainFunction(module, builder, program);

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

    printfFn = null;
    printFnType = null;

    if (Files.exists(executablePath)) {
      return executablePath.toAbsolutePath();
    } else {
      throw new IllegalArgumentException("Could not compile to an executable");
    }
  }

  private static void addMainFunction(LLVMModuleRef module, LLVMBuilderRef builder, HirProgram program) {

    // LLVM Types
    var i32Type = LLVM.LLVMInt32Type();
    var i8Type = LLVM.LLVMInt8Type();
    var i8PointerType = LLVM.LLVMPointerType(i8Type, 0);
    var i8PointerPointerType = LLVM.LLVMPointerType(i8PointerType, 0);
    var mainArgs = new LLVMTypeRef[]{i32Type, i8PointerPointerType};

    // Create main function
    var mainFn = LLVM.LLVMAddFunction(
      module, "main",
      LLVM.LLVMFunctionType(i32Type, new PointerPointer<>(mainArgs), mainArgs.length, 0)
    );
    var entryBlock = LLVM.LLVMAppendBasicBlock(mainFn, "entry");
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

    lower_expression(module, builder, program);


    // Return the result of factorial
//    var zero = LLVM.LLVMConstInt(i32Type, 0, 0);
//    LLVM.LLVMBuildRet(builder, zero);
  }

  public static void lower_expression(LLVMModuleRef module, LLVMBuilderRef builder, HirExpression hirExpression) {

    switch (hirExpression) {
      case HirProgram hir -> {
        for (final var expr : hir.expressions()) {
          lower_expression(module, builder, expr);
        }
      }
//      case HirBinaryOperation hir -> lower_llvm_binary_operation(module, builder, hir);
      case HirNoOp hir -> {
        // Do nothing
      }
      default -> translate_expression(module, builder, hirExpression);

        //throw new IllegalArgumentException(STR."Unknown expression '\{hirExpression.getClass().getSimpleName()}'");
    }
  }

  private static LLVMTypeRef printFnType;
  private static LLVMValueRef printfFn;

  private static LLVMValueRef lower_llvm_call(LLVMModuleRef module, LLVMBuilderRef builder, HirCall hir) {

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

      final var llvmArg = translate_expression(module, builder, hir.arguments()[i]);
      llvmArgs[i] = llvmArg;
    }

//    var format = LLVMBuildGlobalStringPtr(builder, "%ld\n", "format");

    return LLVM.LLVMBuildCall2(builder, fnType, fn, new PointerPointer<>(llvmArgs), llvmArgs.length, STR."call \{functionName}");
  }

  private static LLVMValueRef lower_llvm_return(LLVMModuleRef module, LLVMBuilderRef builder, HirReturn hir) {

    final var expr = translate_expression(module, builder, hir.expression());
    return LLVM.LLVMBuildRet(builder, expr);
  }

  private static LLVMValueRef lower_llvm_binary_operation(LLVMModuleRef module, LLVMBuilderRef builder, HirBinaryOperation hir) {

    final var lhs = translate_expression(module, builder, hir.lhs());
    final var rhs = translate_expression(module, builder, hir.rhs());

    return switch (hir.type()) {
      case ADD -> LLVM.LLVMBuildAdd(builder, lhs, rhs, "lhs + rhs");
      case SUBTRACT -> LLVM.LLVMBuildSub(builder, lhs, rhs, "lhs - rhs");
      case MULTIPLY -> LLVM.LLVMBuildMul(builder, lhs, rhs, "lhs * rhs");
      case DIVIDE -> LLVM.LLVMBuildFDiv(builder, lhs, rhs, "lhs / rhs");
      default -> throw new IllegalArgumentException(STR."Unknown binary operation kind '\{hir.type()}'");
    };
  }

  private static LLVMValueRef translate_expression(LLVMModuleRef module, LLVMBuilderRef builder, HirExpression hirExpression) {

    return switch (hirExpression) {
      case HirLiteral hir -> translate_literal(module, builder, hir);
      case HirBinaryOperation hir -> lower_llvm_binary_operation(module, builder, hir);
      case HirArgument hir -> lower_llvm_argument(module, builder, hir);
      case HirCall hir -> lower_llvm_call(module, builder, hir);
      case HirReturn hir -> lower_llvm_return(module, builder, hir);
      default -> throw new IllegalArgumentException(STR."Unknown expression '\{hirExpression.getClass().getSimpleName()}'");
    };
  }

  private static LLVMValueRef lower_llvm_argument(LLVMModuleRef module, LLVMBuilderRef builder, HirArgument hir) {
    return translate_expression(module, builder, hir.expression());
  }

  private static LLVMValueRef translate_literal(LLVMModuleRef module, LLVMBuilderRef builder, HirLiteral hirLiteral) {

    return switch (hirLiteral.literal()) {
      case Integer v -> LLVM.LLVMConstInt(getType("i32"), v, 0);
      case String v -> LLVM.LLVMBuildGlobalStringPtr(builder, v, v);
      default -> throw new IllegalArgumentException(STR."Unknown literal '\{hirLiteral.literal()}'");
    };

//    LLVM.LLVMConstInt(i32Type, 0, 0);
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
