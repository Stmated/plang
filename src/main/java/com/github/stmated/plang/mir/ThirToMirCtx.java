package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirFnSignature;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.thir.raising.ThirRaiseResult;
import java.util.ArrayDeque;
import java.util.Deque;
import lombok.Value;

@Value
public class ThirToMirCtx {

  public record LoopHandle(MirNode next, MirNode exit) {

  }

  ThirToMirCtx parent;
  Deque<LoopHandle> loopStack = new ArrayDeque<>();
  Deque<MirNode> nodeStack = new ArrayDeque<>();
  Deque<MirScope> scopeStack = new ArrayDeque<>();
  Deque<MirFnSignature> fnStack = new ArrayDeque<>();

  ThirRaiseResult thirRaiseResult;
}
