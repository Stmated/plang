package com.github.stmated.plang.parser;

import com.github.stmated.plang.PlangCompiler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class PlangTestUtil {

  public static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }

  public static Stream<Arguments> testShouldSucceedSource() throws IOException {

    final var paths = new ArrayList<Path>();
    PlangCompiler.find(Paths.get("src/test/resources/plang/valid_parse"), paths);

    return paths.stream().map(Arguments::of);
  }
}
