package org.inf.llvm.lowering;

import lombok.RequiredArgsConstructor;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.inf.exceptions.NotImplementedException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.ty.*;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * TODO: Maybe someday. Though it seems like never, since we might as well go with AST -> HIR -> MIR right away since it might be needed some later day
 */
@RequiredArgsConstructor
public class ThirToLLVMLowering {

  private final Pattern PATTERN_INTEGER_SUFFIX = Pattern.compile("(\\d+)([iu])(\\d+)");

  public static LoweringResult start(Hir.Expression hir) {

    //final var typeResolver = new LLVMTypeResolver()

//    final var lowering = new ThirToLLVMLowering();
//    return lowering.lower(hir);
//
    return null;
  }

  private final LLVMCtx ctx;
  private final LLVMTypeResolver typeResolver;
  private final LLVMValueResolver valueResolver;

  private LoweringResult lower(Hir.Expression expr) {

    return switch (expr) {
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
  }

  private LoweringResult lower_path(Hir.Path it) {
    return null;
  }

  private LoweringResult lower_new_by_ctor(Hir.NewByCtor it) {
    return null;
  }

  private LoweringResult lower_new_by_block(Hir.NewByBlock it) {
    return null;
  }

  private LoweringResult lower_struct(Hir.Struct it) {
    return null;
  }

  private LoweringResult lower_array_access(Hir.ArrayAccess it) {
    return null;
  }

  private LoweringResult lower_array(Hir.Array it) {
    return null;
  }

  private LoweringResult lower_function_signature(Hir.FunctionSignature it) {
    return null;
  }

  private LoweringResult lower_function(Hir.Function it) {
    return null;
  }

  private LoweringResult lower_expressions(Hir.Expression[] children) {
    return null;
  }

  private LoweringResult lower_loop_continue(Hir.LoopContinue hir) {
    return null;
  }

  private LoweringResult lower_loop_break(Hir.LoopBreak hir) {
    return null;
  }

  private LoweringResult lower_loop(Hir.Loop hir) {
    return null;
  }

  private LoweringResult lower_identifier(Hir.Identifier hir) {
    return null;
  }

  private LoweringResult lower_assignment(Hir.Assignment hir) {
    return null;
  }

  private LoweringResult lower_variable_declaration(Hir.Dec hir) {
    return null;
  }

  private LoweringResult lower_block(Hir.Block hir) {
    return null;
  }

  private LoweringResult lower_conditional(Hir.Conditional hir) {
    return null;
  }

  private LoweringResult lower_call(Hir.Call hir) {
    return null;
  }

  private LoweringResult lower_argument(Hir.Argument hir) {
    return null;
  }

  private LoweringResult lower_binary_operation(Hir.BinaryOperation hir) {
    return null;
  }

  private LoweringResult lower_return(Hir.Return hir) {
    return null;
  }

  private LoweringResult lower_literal(Hir.Literal literal) {

    return switch (literal.ty()) {
      case TyValueString str -> lower_literal_string(literal.content(), str);
      case TyValueNumberInteger ni -> lower_literal_number_integer(literal, ni);
      case TyValueNumberPrecisioned np -> lower_literal_number_precisioned(literal.content(), literal, np);
      case TyValueBoolean b -> lower_literal_boolean(literal.content(), b);
      default -> throw new UnexpectedExpressionException(literal);
    };
  }

  private LoweringResult lower_literal_string(String content, TyValueString str) {

    // TODO: Like in Rust, should we separate the different kinds of strings into different types? Global, char array, others?

//    final var globalString = getGlobalStringPtr(content);
//    overridingTypes.put(globalString, new TyPointer(Ty.CHAR));
//
//    return globalString;

    final var array = createCharArray(content);
//    overridingTypes.put(array.ref(), array.ty());

    return new LoweringResult(array.ref());
  }

  public ArrayAndSize createCharArray(String str) {

    final var bytes = (str + "\u0000").getBytes(StandardCharsets.UTF_8);
    final var charArray = new LLVMValueRef[bytes.length];
    final var elementTy = Ty.CHAR;
    final var charType = typeResolver.resolve(elementTy);
    for (int i = 0; i < bytes.length; i++) {
      charArray[i] = valueResolver.getByte(bytes[i]);
    }

    final var strArray = LLVM.LLVMConstArray2(
      charType, new PointerPointer<>(charArray), bytes.length
    );

    final var arrayTy = new TyValueArray(elementTy, bytes.length);
    final var charArrayType = typeResolver.resolve(arrayTy);
    final var globalVar = LLVM.LLVMAddGlobal(ctx.module(), charArrayType, "gs");
    LLVM.LLVMSetInitializer(globalVar, strArray);

    return new ArrayAndSize(globalVar, arrayTy);
  }

  private LoweringResult lower_literal_number_integer(Hir.Literal literal, TyValueNumberInteger ty) {

    // TODO: Wrong? Or can it handle octal, hex and binary? Need tests

    final var content = literal.content();
    final var v = parseLiteralInteger(content, ty.radix());
    final var typeRef = typeResolver.resolve(ty);
    final var constant = LLVM.LLVMConstInt(typeRef, v, ty.signed() ? 1 : 0);

    return new LoweringResult(giveConstantOrAlloca(constant, typeRef, literal, ty));
  }

  private int parseLiteralInteger(String content, int radix) {

    final var matcher = PATTERN_INTEGER_SUFFIX.matcher(content);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1), radix);
    }

    return Integer.parseInt(content, radix);
  }

  private LoweringResult lower_literal_number_precisioned(String content, Hir.Expression instr, TyValueNumberPrecisioned ty) {

    final var v = Double.parseDouble(content);
    final var typeRef = typeResolver.resolve(ty);
    final var constant = LLVM.LLVMConstReal(typeRef, v);

    return new LoweringResult(giveConstantOrAlloca(constant, typeRef, instr, ty));
  }

  private LoweringResult lower_literal_boolean(String strValue, TyValueBoolean b) {
    final var value = Boolean.parseBoolean(strValue);
    return new LoweringResult(LLVM.LLVMConstInt(typeResolver.resolve(b), value ? 1 : 0, 0));
  }

  private LLVMValueRef giveConstantOrAlloca(LLVMValueRef constant, LLVMTypeRef typeRef, Hir.Expression expr, TyValueNumber ty) {

    // TODO: Is it okay to always give back constant, and then let "store" be what makes it alloca? Need to run some code :)
    return constant;
  }
}
