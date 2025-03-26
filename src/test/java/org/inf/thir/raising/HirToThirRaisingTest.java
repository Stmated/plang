package org.inf.thir.raising;

import org.inf.Inf;
import org.inf.ty.TyValueKind;
import org.inf.ty.TyValueNumber;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HirToThirRaisingTest {

  @Test
  void testAdditionInteger() {

    final var thir = Inf.codeToThir("1 + 1");

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(TyValueKind.INTEGER, ((TyValueNumber)thir.root().ty()).getValueKind());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 01",
    "1 + 0b1",
    "1 + 0x1",
    "1 + 0B1",
    "1 + 0X1"
  })
  void testAdditionOnePlusOneDifferentForms(String code) {

    final var thir = Inf.codeToThir(code);

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(TyValueKind.INTEGER, ((TyValueNumber)thir.root().ty()).getValueKind());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 1l",
    "1 + 11L"
  })
  void testAdditionLong(String code) {

    final var thir = Inf.codeToThir(code);

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(64, ((TyValueNumber)thir.root().ty()).width().value());
  }

  /**
   * TODO: This should automatically be converted into width 64 since the values are constants. But for now it will be 32 width and overflow.
   */
  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 3147483647", // Way too big for int
    "1 + 2147483647" // Too big for int by just 1
  })
  void testAdditionLongByDeductionIsNotAllowed(String code) {

    final var thir = Inf.codeToThir(code);

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(32, ((TyValueNumber)thir.root().ty()).width().value());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 1.1",
    "1 + 1.1d",
    "1 + 1.1D"
  })
  void testAdditionIntegerAndDouble(String code) {

    final var thir = Inf.codeToThir(code);

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(TyValueKind.DOUBLE, ((TyValueNumber)thir.root().ty()).getValueKind());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 1.1m",
    "1 + 1.1M"
  })
  void testAdditionIntegerAndDecimal(String code) {

    final var thir = Inf.codeToThir(code);

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(TyValueKind.DECIMAL, ((TyValueNumber)thir.root().ty()).getValueKind());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 1.1f",
    "1 + 1.1F"
  })
  void testAdditionIntegerAndFloat(String code) {

    final var thir = Inf.codeToThir(code);

    Assertions.assertInstanceOf(TyValueNumber.class, thir.root().ty());
    Assertions.assertEquals(TyValueKind.FLOAT, ((TyValueNumber)thir.root().ty()).getValueKind());
  }
}
