package org.inf.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.global.LLVM;
import org.inf.ty.*;
import org.inf.ty.util.Tys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LLVMTypeResolverTest {

  static Stream<Arguments> projectedTypes() {
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.LONG),
      Arguments.of(Ty.BOOLEAN, Ty.INTEGER),
      Arguments.of(Ty.VOID, Ty.DEADEND),
      Arguments.of(new TyValueArray(Ty.INTEGER, 2), Ty.BOOLEAN),
      Arguments.of(new TyStruct(new TyField[]{new TyField("value", Ty.INTEGER)}), Ty.BOOLEAN),
      Arguments.of(new TyFn(new TyParam[]{new TyParam("value", Ty.INTEGER)}, false, Ty.INTEGER), Ty.BOOLEAN)
    );
  }

  @ParameterizedTest
  @MethodSource("projectedTypes")
  void given__type_info__when__resolved_by_llvm__then__constraint_and_structure_do_not_change_the_abi_type(
    final Ty completion, final Ty structural
  ) {
    final var context = LLVM.LLVMContextCreate();
    try {
      final var resolver = new LLVMTypeResolver(context, new Cache<Ty, LLVMTypeRef>());
      final var expected = resolver.resolve(completion);
      final var actual = resolver.resolve(Tys.typeInfo(completion, structural, Ty.INFER));
      assertEquals(expected.address(), actual.address());
    } finally {
      LLVM.LLVMContextDispose(context);
    }
  }
}
