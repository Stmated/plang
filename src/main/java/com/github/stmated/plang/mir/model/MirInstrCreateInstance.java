package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import java.util.Arrays;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrCreateInstance extends AbstractMirInstr {

  Ty ty;

  MirInstr allocator;

  /**
   * The arguments will be in the order of the fields of the type that we are creating an instance of. Either all fields must be present, or all fields of the
   * constructor must be.
   */
  MirInstr[] arguments;

  @Override
  public String toString() {
    return STR."new \{allocator} \{ty}(\{Arrays.toString(arguments)})";
  }
}
