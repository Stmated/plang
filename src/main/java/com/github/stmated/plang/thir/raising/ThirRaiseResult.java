package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.hir.Hir;
import lombok.Value;

@Value
public class ThirRaiseResult {

  Hir.Expression root;
}
