package com.github.stmated.plang;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.bytedeco.llvm.global.LLVM.*;

class LLVMPlaygroundTest {

  // a 'char *' used to retrieve error messages from LLVM
  private static final BytePointer error = new BytePointer();

  @Test
  void testFactorial() throws IOException, InterruptedException {

    // Stage 1: Initialize LLVM components
    LLVMInitializeCore(LLVMGetGlobalPassRegistry());
    LLVMLinkInMCJIT();
    LLVMInitializeNativeAsmPrinter();
    LLVMInitializeNativeAsmParser();
    LLVMInitializeNativeTarget();

    // Stage 2: Build the factorial function.
    LLVMContextRef context = LLVMContextCreate();
    LLVMModuleRef module = LLVMModuleCreateWithNameInContext("factorial", context);
    LLVMBuilderRef builder = LLVMCreateBuilderInContext(context);
    LLVMTypeRef i32Type = LLVMInt32TypeInContext(context);
    LLVMTypeRef factorialType = LLVMFunctionType(i32Type, i32Type, 1, 0);

    LLVMValueRef factorial = LLVMAddFunction(module, "factorial", factorialType);
    LLVMSetFunctionCallConv(factorial, LLVMCCallConv);

    LLVMValueRef n = LLVMGetParam(factorial, 0);
    LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
    LLVMValueRef one = LLVMConstInt(i32Type, 1, 0);
    LLVMBasicBlockRef entry = LLVMAppendBasicBlockInContext(context, factorial, "entry");
    LLVMBasicBlockRef ifFalse = LLVMAppendBasicBlockInContext(context, factorial, "if_false");
    LLVMBasicBlockRef exit = LLVMAppendBasicBlockInContext(context, factorial, "exit");

    LLVMPositionBuilderAtEnd(builder, entry);
    LLVMValueRef condition = LLVMBuildICmp(builder, LLVMIntEQ, n, zero, "condition = n == 0");
    LLVMBuildCondBr(builder, condition, exit, ifFalse);

    LLVMPositionBuilderAtEnd(builder, ifFalse);
    LLVMValueRef nMinusOne = LLVMBuildSub(builder, n, one, "nMinusOne = n - 1");
    PointerPointer<Pointer> arguments = new PointerPointer<>(1)
        .put(0, nMinusOne);
    LLVMValueRef factorialResult = LLVMBuildCall2(builder, factorialType, factorial, arguments, 1, "factorialResult = factorial(nMinusOne)");
    LLVMValueRef resultIfFalse = LLVMBuildMul(builder, n, factorialResult, "resultIfFalse = n * factorialResult");
    LLVMBuildBr(builder, exit);

    LLVMPositionBuilderAtEnd(builder, exit);
    LLVMValueRef phi = LLVMBuildPhi(builder, i32Type, "result");
    PointerPointer<Pointer> phiValues = new PointerPointer<>(2)
        .put(0, one)
        .put(1, resultIfFalse);
    PointerPointer<Pointer> phiBlocks = new PointerPointer<>(2)
        .put(0, entry)
        .put(1, ifFalse);
    LLVMAddIncoming(phi, phiValues, phiBlocks, 2);
    LLVMBuildRet(builder, phi);

    // Stage 3: Verify the module using LLVMVerifier
    if (LLVMVerifyModule(module, LLVMPrintMessageAction, error) != 0) {
      LLVMDisposeMessage(error);
      return;
    }

    // Stage 4: Create a pass pipeline using the legacy pass manager
    LLVMPassManagerRef pm = LLVMCreatePassManager();
//    LLVMAddAggressiveInstCombinerPass(pm);
    LLVMAddNewGVNPass(pm);
    LLVMAddCFGSimplificationPass(pm);
    LLVMRunPassManager(pm, module);
//    LLVMDumpModule(module);

    // Stage 5: Execute the code using MCJIT
//    LLVMExecutionEngineRef engine = new LLVMExecutionEngineRef();
//    LLVMMCJITCompilerOptions options = new LLVMMCJITCompilerOptions();
//    if (LLVMCreateMCJITCompilerForModule(engine, module, options, 3, error) != 0) {
//      System.err.println("Failed to create JIT compiler: " + error.getString());
//      LLVMDisposeMessage(error);
//      return;
//    }
//
//    LLVMGenericValueRef argument = LLVMCreateGenericValueOfInt(i32Type, 20, 0);
//    LLVMGenericValueRef result = LLVMRunFunction(engine, factorial, /* argumentCount */ 1, argument);
//    System.out.println();
//    System.out.println("; Running factorial(" + LLVMGenericValueToInt(argument, 0) + ") with MCJIT...");
//    System.out.println("; Result: " + LLVMGenericValueToInt(result, /* signExtend */ 0));

    addMainFunction(module, builder);

    // Save the IR to a file.
    String filename = "factorial.ll";
    LLVMWriteBitcodeToFile(module, filename);

    LLVMDumpModule(module);

//    LLVMDisposePassManager(pm);
//    LLVMDisposeBuilder(builder);
//    LLVMContextDispose(context);

    // Compile the .ll to .o using clang/llvm
    executeCommand("clang -c factorial.ll -o factorial.o");
    executeCommand("clang factorial.o -o run");

    // Link the .o to .out (executable) using clang/llvm
//    Runtime.getRuntime().exec("clang factorial.o -o run").waitFor();

    // Stage 6: Dispose of the allocated resources
//    LLVMDisposeExecutionEngine(engine);
    LLVMDisposePassManager(pm);
    LLVMDisposeBuilder(builder);
    LLVMContextDispose(context);
  }

  private void addMainFunction(LLVMModuleRef module, LLVMBuilderRef builder) {

    // LLVM Types
    var i32Type = LLVMInt32Type();
    var i8Type = LLVMInt8Type();
    var i8PointerType = LLVMPointerType(i8Type, 0);
    var i8PointerPointerType = LLVMPointerType(i8PointerType, 0);
    var mainArgs = new LLVMTypeRef[] {i32Type, i8PointerPointerType};

    // Create main function
    LLVMValueRef mainFn = LLVMAddFunction(
        module, "main",
        LLVMFunctionType(i32Type, new PointerPointer<>(mainArgs), mainArgs.length, 0)
    );
    LLVMBasicBlockRef entryBlock = LLVMAppendBasicBlock(mainFn, "entry");
    LLVMPositionBuilderAtEnd(builder, entryBlock);

    // [OPTIONAL] Create factorial argument
    LLVMValueRef factorialFn = LLVMGetNamedFunction(module, "factorial");

    LLVMTypeRef factorialType = LLVMFunctionType(i32Type, i32Type, 1, 0);

    var arguments = new PointerPointer<>(1)
        .put(0, LLVMConstInt(i32Type, 30, 0));

    LLVMValueRef factorialCall = LLVMBuildCall2(
        builder,
        factorialType,
        factorialFn,
        arguments, 1, "factorialtmp"
    );

    LLVMTypeRef[] printfArgs = {i8PointerType};
    var printFnType = LLVMFunctionType(i32Type, new PointerPointer<>(printfArgs), printfArgs.length, 1);
    LLVMValueRef printfFn = LLVMAddFunction(module, "printf", printFnType);

    var format = LLVMBuildGlobalStringPtr(builder, "%ld\n", "format");
    LLVMBuildCall2(builder, printFnType, printfFn, new PointerPointer<>(format, factorialCall), 2, "printfCall");

    // Return the result of factorial
    LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
    LLVMBuildRet(builder, zero);
  }

  private static void executeCommand(String command) throws IOException, InterruptedException {

    var p = Runtime.getRuntime().exec(command);
    try (var error = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
      String line;
      while ((line = error.readLine()) != null) {
        System.out.println(line);
      }
    }

    p.waitFor();
  }
}
