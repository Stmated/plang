package org.inf;

import org.inf.ty.util.MachineTarget;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

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
