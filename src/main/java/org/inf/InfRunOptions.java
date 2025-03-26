package org.inf;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class InfRunOptions extends InfCompileOptions {

  Object[] arguments;
  @Builder.Default
  boolean includeCppLibs = false;
  @Builder.Default
  int optLevel = 0;
}
