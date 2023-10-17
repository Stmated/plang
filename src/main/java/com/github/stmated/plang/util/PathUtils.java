package com.github.stmated.plang.util;

import com.github.stmated.plang.StreamCreator;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;

public class PathUtils {

  private static final PathMatcher plangPatchMatcher
    = FileSystems.getDefault().getPathMatcher("glob:*.plang");

//  public void compileDirectory(Path sourceDirectory, StreamCreator streamCreator) throws IOException {
//
//    final var paths = new ArrayList<Path>();
//    PlangCompiler.find(sourceDirectory, paths);
//
//    for (final var path : paths) {
//      this.compile(path, streamCreator);
//    }
//  }

  public static void find(Path fileOrDirectory, Collection<Path> target) throws IOException {

    if (Files.isDirectory(fileOrDirectory)) {

      try (final var children = Files.list(fileOrDirectory)) {
        for (final var child : children.toList()) {
          PathUtils.find(child, target);
        }
      }

    } else {
      if (plangPatchMatcher.matches(fileOrDirectory.getFileName())) {
        target.add(fileOrDirectory);
      }
    }
  }
}
