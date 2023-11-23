package com.github.stmated.plang;

import com.github.stmated.plang.ty.util.MachineTarget;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@Value
@NonFinal
@SuperBuilder
public class PlangCompileOptions {

  @NonFinal
  MachineTarget machineTarget;

  MachineTarget machineTarget() {
    if (machineTarget == null) {
      machineTarget = new MachineTarget(64);
    }

    return machineTarget;
  }
}
