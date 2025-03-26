package org.inf.llvm.lowering;

import org.inf.exceptions.GenericLLVMException;
import org.inf.exceptions.UnreachableCodeLLVMException;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.global.LLVM;

@UtilityClass
class LLVMUtils {

  public static void verifyModule(LLVMModuleRef module) {

    final var error = new BytePointer();
    try {

      if (LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, error) != 0) {

        final var details = LLVM.LLVMPrintModuleToString(module).getString();

        final var errorMessage = error.getString();
        LLVM.LLVMDisposeMessage(error);

        throw map_error_message_to_exception(errorMessage, details);
      }

    } catch (Throwable t) {

      if (t instanceof RuntimeException re) {
        throw re;
      }

      throw new IllegalStateException("Could not verify module, because: " + t);
    } finally {
      error.deallocate();
    }
  }

  public static GenericLLVMException map_error_message_to_exception(String errorMessages, String details) {

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
}
