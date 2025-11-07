package org.inf.execution.interpreter;

import org.inf.mir.Mir;

import java.util.HashMap;
import java.util.Map;

public class Scope {

  public final Map<Long, Object> valueMap = new HashMap<>();
  public final Map<Long, Mir.InstrCreateFn> functions = new HashMap<>();
  public Object result;
}
