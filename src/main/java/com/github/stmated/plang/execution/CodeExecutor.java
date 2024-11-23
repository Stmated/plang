package com.github.stmated.plang.execution;

import com.github.stmated.plang.mir.model.MirNode;

public interface CodeExecutor {

  Object execute(MirNode node);
}
