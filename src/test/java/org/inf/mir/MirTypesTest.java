package org.inf.mir;

import org.inf.ty.*;
import org.inf.ty.util.Tys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MirTypesTest {

  static Stream<Arguments> projectedTypes() {
    final var array = new TyValueArray(Ty.INTEGER, 2);
    final var struct = new TyStruct(new TyField[]{new TyField("value", Ty.INTEGER)});
    final var fn = new TyFn(new TyParam[]{new TyParam("value", Ty.INTEGER)}, false, Ty.INTEGER);
    return Stream.of(
      Arguments.of(Ty.INTEGER, Ty.BOOLEAN, Ty.INTEGER),
      Arguments.of(Ty.VOID, Ty.DEADEND, Ty.VOID),
      Arguments.of(array, Ty.BOOLEAN, new TyPointer<>(array)),
      Arguments.of(struct, array, new TyPointer<>(struct)),
      Arguments.of(fn, Ty.BOOLEAN, new TyPointer<>(fn)),
      Arguments.of(Ty.DEADEND, array, Ty.DEADEND)
    );
  }

  @ParameterizedTest
  @MethodSource("projectedTypes")
  void given__type_info__when__lowered_to_value_and_return_types__then__only_completion_affects_the_backend(
    final Ty completion, final Ty structural, final Ty expected
  ) {
    final var info = Tys.typeInfo(completion, structural, Ty.INFER);
    assertAll(
      () -> assertEquals(expected, MirTypes.valueType(info)),
      () -> assertEquals(completion == Ty.DEADEND ? Ty.VOID : expected, MirTypes.returnType(info)),
      () -> assertSame(Tys.structuralTy(structural), Tys.structuralTy(info)),
      () -> assertSame(Ty.INFER, Tys.declaredConstraintTy(info))
    );
  }
}
