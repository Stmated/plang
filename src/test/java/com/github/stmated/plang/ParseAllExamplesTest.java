package com.github.stmated.plang;

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

  private static Stream<Arguments> testShouldSucceedSource() throws IOException {

    final var paths = new ArrayList<Path>();
    PlangCompiler.find(Paths.get("src/test/resources/plang/valid_parse"), paths);

    return paths.stream().map(Arguments::of);
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
  }
}
