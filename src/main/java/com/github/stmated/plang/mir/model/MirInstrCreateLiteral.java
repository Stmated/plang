package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrCreateLiteral extends MirInstr {

  String content;
  Ty ty;

  @Override
  public String toString() {
    return STR."\{content}:\{ty.toShortString()}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    MirInstrCreateLiteral that = (MirInstrCreateLiteral) o;
    return Objects.equals(content, that.content) && Objects.equals(ty, that.ty);
  }

//  @Override
//  public int hashCode() {
//    return System.identityHashCode(this);
//  }
}
