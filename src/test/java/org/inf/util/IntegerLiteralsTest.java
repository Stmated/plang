package org.inf.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;

class IntegerLiteralsTest {

  @ParameterizedTest
  @CsvSource({
    "1, 10, 1",
    "1u8, 10, 1",
    "-1i8, 10, -1",
    "1L, 10, 1",
    "1_000, 10, 1000",
    "0x1, 16, 1",
    "-0x1, 16, -1",
    "0b1, 2, 1",
    "0o1, 8, 1",
    "0b1, 16, 177",
    "0b, 16, 11",
    "256u8, 10, 256",
    "99999999999999999999999999999999999999u128, 10, 99999999999999999999999999999999999999"
  })
  void given__integer_literal__when__parsed__then__exact_value_without_width_truncation(String content, int radix, String expected) {
    Assertions.assertEquals(new BigInteger(expected), IntegerLiterals.parse(content, radix));
  }
}
