package org.inf.execution;

import org.inf.mir.model.MirNode;

public interface CodeExecutor {

  Object execute(MirNode node);
}
