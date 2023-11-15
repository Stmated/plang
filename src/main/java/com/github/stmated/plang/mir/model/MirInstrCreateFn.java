package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrCreateFn extends AbstractMirInstr {

  @Nullable
  MirNode entry;
  @NotNull
  MirFnSignature signature;
  @NotNull
  Ty ty;

  @Override
  public String toString() {
    return STR."\{name() == null ? "anon" : name()}\{signature} @ \{entry}";
  }

  @Override
  public Ty ty() {
    return ty;
  }
}
