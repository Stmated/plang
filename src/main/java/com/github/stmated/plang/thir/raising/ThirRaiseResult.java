package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.hir.Hir.Expression;
import com.github.stmated.plang.ty.Ty;
import java.util.Map;
import java.util.Objects;
import lombok.Value;
import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

@Value
public class ThirRaiseResult {

  Expression root;
  Map<Expression, Ty> map;

  @Nullable
  public Ty getType(Expression e) {

    // TODO: Throw exception if not found? Since it *should* always be found. All expressions should always have a resulting kind.
    return map.get(e);
  }

  @NotNull
  public Ty getTypeOrThrow(Expression e) {
    return Objects.requireNonNull(map.get(e), STR."HIR expression '\{e}' did not have a Ty");
  }
}
