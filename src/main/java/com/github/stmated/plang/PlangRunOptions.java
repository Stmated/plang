package com.github.stmated.plang;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlangRunOptions {

  Object[] arguments;
  @Builder.Default
  boolean includeCppLibs = false;
  @Builder.Default
  int optLevel = 0;
}
