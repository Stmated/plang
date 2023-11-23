package com.github.stmated.plang;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class PlangRunOptions extends PlangCompileOptions {

  Object[] arguments;
  @Builder.Default
  boolean includeCppLibs = false;
  @Builder.Default
  int optLevel = 0;
}
