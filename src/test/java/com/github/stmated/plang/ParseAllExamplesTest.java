package com.github.stmated.plang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

class ParseAllExamplesTest {

  @Test
  void parseAllExamples() throws IOException {

    final var compiler = new PlangCompiler();

    final var path = Paths.get("src/test/resources/plang").toAbsolutePath();
    final var files = new HashMap<Path, ByteArrayOutputStream>();

    compiler.compileDirectory(path, sourceFilePath -> {
      final var os = new ByteArrayOutputStream();
      files.put(sourceFilePath, os);
      return os;
    });

    var i = 0;
  }
}
