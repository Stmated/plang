package org.inf.hir;

import org.inf.Inf;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyStruct;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

class HirTupleAccessTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "(10,)[0]",
    "(10, true)[0]",
    "((10,), true)[0][0]",
    "val t: (int, bool) = (10, true); t[0]",
    "val t = (10, true); t[(0)]",
    "val t = (10, true); t[0i8]",
    "val t = (10, true); t[0u8]",
    "val t = (10, true); t[0L]",
    "val t = (false, 10); t[0x1]",
    "val t = (false, 10); t[0b1]",
    "val t = (false, 10); t[001]",
    "val t = (false, 10); t[0_1]",
    "val t = (false, 10); t[1u128]"
  })
  void given__literal_tuple_index__when__typed__then__selected_slot_type(String code) {
    Assertions.assertTrue(TypeComparison.sameValueType(Ty.INTEGER, Inf.codeToThir(code).root().ty()));
  }

  static Stream<Arguments> heterogeneousSlots() {
    return Stream.of(
      Arguments.of("(10, true)[1]", Ty.BOOLEAN),
      Arguments.of("(10, 20L)[1]", Ty.LONG),
      Arguments.of("(10, 2f)[1]", Ty.FLOAT),
      Arguments.of("(10, \"hi\")[1]", Ty.STRING)
    );
  }

  @ParameterizedTest
  @MethodSource("heterogeneousSlots")
  void given__heterogeneous_tuple__when__indexed__then__precise_type(String code, Ty type) {
    Assertions.assertEquals(type, Inf.codeToThir(code).root().ty());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "(10, true)[-1] | Tuple index must not be negative",
    "(10, true)[-1u8] | Tuple index must not be negative",
    "(10, true)[-0x1] | Tuple index must not be negative",
    "(10, true)[-0b1] | Tuple index must not be negative",
    "(10, true)[2] | Tuple index out of range",
    "(10,)[1] | Tuple index out of range",
    "(10, true)[256u8] | Tuple index out of range",
    "(10, true)[99999999999999999999999999999999999999u128] | Tuple index out of range",
    "(10, true)[true] | Tuple index must be an integer literal",
    "(10, true)[1f] | Tuple index must be an integer literal",
    "(10, true)[\"0\"] | Tuple index must be an integer literal",
    "(10, true)[(0,)] | Tuple index must be an integer literal",
    "val i = 0; (10, true)[i] | Computed tuple indices are not supported",
    "(10, true)[0 + 1] | Computed tuple indices are not supported",
    "(10, true)[{ 0 }] | Computed tuple indices are not supported",
    "val index = () => 0; (10, true)[index()] | Computed tuple indices are not supported",
    "val f = () => (1, { return true; }, (10,)[1]); f() | Tuple index out of range"
  })
  void given__invalid_tuple_index__when__typed__then__explicit_diagnostic(String code, String diagnostic) {
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    Assertions.assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "((10, true),)[0]",
    "val t = ((10, true),); t[0]"
  })
  void given__nested_tuple_slot__when__typed__then__aggregate_type(String code) {
    Assertions.assertEquals(
      new TyStruct(new TyField[]{new TyField(null, Ty.INTEGER), new TyField(null, Ty.BOOLEAN)}),
      Inf.codeToThir(code).root().ty()
    );
  }
}
