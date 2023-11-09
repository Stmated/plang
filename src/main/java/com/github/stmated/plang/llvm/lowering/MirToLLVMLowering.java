package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.mir.model.MirFn;
import com.github.stmated.plang.mir.model.MirFnParameter;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyValueArray;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.global.LLVM;

@Slf4j
public class MirToLLVMLowering {

  /**
   * If trying to lower a node as the first layer, then we will wrap it with a MirFn main function.
   */
  public ModuleResult lower(MirNode node, String name) {

    final var mainFn = new MirFn(
      "main",
      node,
      new MirFnParameter[]{
        new MirFnParameter("argc", Ty.INTEGER),
        // Since the type is an array and the size is unknown (-1), then LLVM will likely make this a PointerPointer instead.
        new MirFnParameter("argv", new TyPointer<>(new TyValueArray(Ty.CHAR, -1))),
        new MirFnParameter("env", new TyPointer<>(new TyPointer<>(Ty.CHAR)))
      },
      false,
      Ty.INTEGER
    );

    return lower(mainFn, name);
  }

  public ModuleResult lower(MirFn fn, String name) {

//    LLVM.LLVMInitializeCore(LLVM.LLVMGetGlobalPassRegistry());
    LLVM.LLVMLinkInMCJIT();
    LLVM.LLVMInitializeNativeAsmPrinter();
    LLVM.LLVMInitializeNativeAsmParser();
    LLVM.LLVMInitializeNativeTarget();

    final var disposals = new ArrayList<Runnable>();

    final var context = LLVM.LLVMContextCreate();
    disposals.add(() -> LLVM.LLVMContextDispose(context));

    final var builder = LLVM.LLVMCreateBuilderInContext(context);
    disposals.add(() -> LLVM.LLVMDisposeBuilder(builder));

    final var moduleResults = new ArrayList<ModuleResult>();
    final var ctx = new Ctx(context, builder);

    final var loweringModule = new MirToLLVMLoweringModule(ctx, STR."\{name}-module");
    disposals.add(loweringModule::close);

    moduleResults.add(loweringModule.lower(fn));

    final var modulesEntryFunctions = new ArrayList<LLVMFunctionCallInfo>();

    final LLVMModuleRef module;
    if (moduleResults.size() == 1) {
      final var moduleResult = moduleResults.getFirst();
      if (moduleResult.fn() != null) {
        modulesEntryFunctions.add(moduleResult.fn());
      }
      disposals.add(moduleResult.disposeCallback());
      module = moduleResult.module();
    } else if (moduleResults.size() > 1) {

      module = LLVM.LLVMModuleCreateWithNameInContext(name, context);
      disposals.add(() -> LLVM.LLVMDisposeModule(module));

      // NOTE: Change this according to the actual target!
      LLVM.LLVMSetTarget(module, "arm64-apple-macosx14.0.0");

      for (final var moduleResult : moduleResults) {

        LLVM.LLVMLinkModules2(module, moduleResult.module());

        if (moduleResult.fn() != null) {
          modulesEntryFunctions.add(moduleResult.fn());
        }

        disposals.add(moduleResult.disposeCallback());
      }

    } else {
      throw new IllegalArgumentException("There were no modules created");
    }

    if (!modulesEntryFunctions.isEmpty()) {
      var mainFunction = LLVM.LLVMGetNamedFunction(module, "main");
      if (mainFunction == null) {

        log.debug("Could NOT find a main function inside module, will add one that calls any exposed functions given by build modules");

        final var mainFn = MirToLLVMUtils.createMainFunction(context, module, builder);

        LLVM.LLVMPositionBuilderAtEnd(builder, mainFn.block());

        for (final var moduleEntryFn : modulesEntryFunctions) {
          LLVM.LLVMBuildCall2(
            builder,
            moduleEntryFn.fnType(), moduleEntryFn.fn(),
            moduleEntryFn.arguments(), moduleEntryFn.argumentCount(),
            moduleEntryFn.name()
          );
        }
      } else {
        log.debug("Found main function inside module, so will not add a custom one");
      }
    } else {
      log.debug("Not given any module entry functions; will assume that one of them declares a main function");
    }

    return new ModuleResult(
      module,
      null,
      () -> {

        // Dispose in reverse order
        for (var i = disposals.size() - 1; i >= 0; i--) {
          disposals.get(i).run();
        }
      }
    );
  }

  public Path lower(MirNode node, Path output) throws IOException, InterruptedException {

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

    final var moduleResult = lower(node, outputName);

    final var directoryPath = output.toAbsolutePath().getParent();
    if (!Files.exists(directoryPath)) {
      Files.createDirectories(directoryPath);
    }

    final var bitcodePath = directoryPath.resolve(STR."\{outputName}.ll").toAbsolutePath();
    final var objectPath = directoryPath.resolve(STR."\{outputName}.o").toAbsolutePath();
    final var executablePath = directoryPath.resolve(STR."\{outputName}\{extension}").toAbsolutePath();

    var pm = LLVM.LLVMCreatePassManager();
//    LLVM.LLVMAddAggressiveInstCombinerPass(pm);
//    LLVM.LLVMAddInstructionCombiningPass(pm);
//    LLVM.LLVMAddEarlyCSEPass(pm); // Common Subexpression Elimination
//    LLVM.LLVMAddPromoteMemoryToRegisterPass(pm);
//    LLVM.LLVMAddInstructionCombiningPass(pm);
//    LLVM.LLVMAddReassociatePass(pm); // Change order of operations, making constants ranked better, etc
//    LLVM.LLVMAddNewGVNPass(pm); // Global Value Numbering pass
//    LLVM.LLVMAddCFGSimplificationPass(pm);
//    LLVM.LLVMAddLICMPass(pm); // Loop Invariant Code Motion -- hoise code to header or exit
//    LLVM.LLVMAddIndVarSimplifyPass(pm);
//    LLVM.LLVMAddLoopIdiomPass(); // Replace idioms like zeroing array content with one memset
//    LLVM.LLVMAddLoopUnrollPass(pm);
//    LLVM.LLVMAddAggressiveInstCombinerPass(pm);
//    LLVM.LLVMAddInstructionCombiningPass(pm);
//    LLVM.LLVMAddAggressiveDCEPass(); // Dead Code Elimination

    LLVM.LLVMRunPassManager(pm, moduleResult.module());

    if (log.isTraceEnabled()) {
      log.trace(LLVM.LLVMPrintModuleToString(moduleResult.module()).getString());
    }

    try {
      MirToLLVMUtils.verifyModule(moduleResult.module());

      log.debug("Will now output module to {}", bitcodePath);

      LLVM.LLVMWriteBitcodeToFile(moduleResult.module(), bitcodePath.toString());


//      final var engine = new LLVMExecutionEngineRef();
//      final var options = new LLVMMCJITCompilerOptions();
//      if (LLVM.LLVMCreateMCJITCompilerForModule(engine, module, options, 3, error) != 0) {
//        System.err.println("Failed to create JIT compiler: " + error.getString());
//        LLVM.LLVMDisposeMessage(error);
//        return;
//      }
//
//      final var argument = LLVMCreateGenericValueOfInt(i32Type, 10, /* signExtend */ 0);
//      final var result = LLVMRunFunction(engine, factorial, /* argumentCount */ 1, argument);
//      System.out.println();
//      System.out.println("; Running factorial(10) with MCJIT...");
//      System.out.println("; Result: " + LLVM.LLVMGenericValueToInt(result, /* signExtend */ 0));


      // Compile the .ll to .o using clang/llvm
      executeCommand(new String[]{"clang", "-c", bitcodePath.toString(), "-o", objectPath.toString()});
      executeCommand(new String[]{"clang", objectPath.toString(), "-o", executablePath.toString()});

      LLVM.LLVMWriteBitcodeToFile(moduleResult.module(), bitcodePath.toString());
    } finally {

      //LLVM.LLVMDisposeExecutionEngine(engine);
      LLVM.LLVMDisposePassManager(pm);
      moduleResult.disposeCallback().run();
    }

    return executablePath;
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
