package org.inf.ty.util;

public record MachineTarget(
  int pointerBitSize
) {

  public static final MachineTarget T64 = MachineTarget.T64;
}
