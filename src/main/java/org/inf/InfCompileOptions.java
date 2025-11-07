package org.inf;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.inf.ty.util.MachineTarget;

@Value
@NonFinal
@SuperBuilder
public class InfCompileOptions {

  @NonFinal
  MachineTarget machineTarget;

  MachineTarget machineTarget() {
    if (machineTarget == null) {
      machineTarget = new MachineTarget(64);
    }

    return machineTarget;
  }
}
