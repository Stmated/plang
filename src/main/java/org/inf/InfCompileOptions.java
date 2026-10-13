package org.inf;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.inf.ty.util.MachineTarget;

import java.util.Random;

@Value
@NonFinal
@SuperBuilder
public class InfCompileOptions {

  @NonFinal
  MachineTarget machineTarget;

  @NonFinal
  Random random;

  MachineTarget machineTarget() {
    if (machineTarget == null) {
      machineTarget = MachineTarget.T64;
    }

    return machineTarget;
  }

  Random random() {
    if (random == null) {
      random = new Random();
    }

    return random;
  }
}
