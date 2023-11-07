package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

public record MirFn(
  @Nullable
  String name,
  @Nullable
  MirNode entry,
  @NotNull
  MirFnParameter[] parameters,
  boolean vararg,
  @NotNull
  Ty returnType
) {

}
