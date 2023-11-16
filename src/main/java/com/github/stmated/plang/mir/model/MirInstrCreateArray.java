package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import java.util.Arrays;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrCreateArray extends AbstractMirInstr {

  MirInstr[] elements;
//  MirInstr initializer;
  MirInstr length;
  Ty ty;
  Ty elementTy;

  @Override
  public String toString() {


    final var elementStrings = Arrays.stream(elements()).map(Object::toString).toList();
    final var elementsString = String.join(", ", elementStrings);

    return STR."[\{elementsString};\{ty().toShortString()};\{length()}]";
  }
}
