package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.hir.Hir;
import lombok.Value;

@Value
public class ThirRaiseResult {

  Hir.Expression root;
//  Map<Expression, Ty> map;

//  @Nullable
//  public Ty getType(Hir.Expression e) {
//
//    // TODO: Throw exception if not found? Since it *should* always be found. All expressions should always have a resulting kind.
//    throw new IllegalArgumentException("Get the type from the HIR itself");
//
//    //return map.get(e);
//  }

//  @NotNull
//  public Ty getTypeOrThrow(Hir.Expression e) {
//    throw new IllegalArgumentException("Get the type from the HIR itself");
//
////    return Objects.requireNonNull(map.get(e), STR."HIR expression '\{e}' did not have a Ty");
//  }
}
