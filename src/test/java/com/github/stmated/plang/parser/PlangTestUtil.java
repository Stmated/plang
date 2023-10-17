package com.github.stmated.plang.parser;

import com.github.stmated.plang.util.PathUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class PlangTestUtil {

  public static InputStream stringToStream(String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
  }

  public static List<Path> getTestFilePaths() throws IOException {

    final var paths = new ArrayList<Path>();
    PathUtils.find(Paths.get("src/test/resources/plang/valid_parse"), paths);

    return paths;
  }

  public static Stream<Arguments> testShouldSucceedSource() throws IOException {
    return getTestFilePaths().stream().map(Arguments::of);
  }
}
