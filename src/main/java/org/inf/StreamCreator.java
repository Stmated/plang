package org.inf;

import java.io.OutputStream;
import java.nio.file.Path;

@FunctionalInterface
public interface StreamCreator {

  OutputStream create(Path file);
}
