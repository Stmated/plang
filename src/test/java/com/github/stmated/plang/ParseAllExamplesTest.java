package com.github.stmated.plang;

import com.github.stmated.plang.parser.PlangTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;

class ParseAllExamplesTest {

  public static Stream<Arguments> testShouldSucceedSource() throws IOException {
    return PlangTestUtil.testShouldSucceedSource();
  }

  @ParameterizedTest
  @MethodSource("testShouldSucceedSource")
  void testShouldSucceed(Path path) throws IOException {

    final var compiler = new PlangCompiler();

    final var files = new HashMap<Path, ByteArrayOutputStream>();

    compiler.compileDirectory(path, sourceFilePath -> {
      final var os = new ByteArrayOutputStream();
      files.put(sourceFilePath, os);
      return os;
    });

    Assertions.assertTrue(files.size() > 0);
  }
}
