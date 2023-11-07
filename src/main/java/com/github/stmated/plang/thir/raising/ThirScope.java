package com.github.stmated.plang.thir.raising;

import com.github.stmated.plang.ty.Ty;
import java.util.HashMap;
import java.util.Map;

public record ThirScope(
  ThirScope parent,
  String name,
  Map<String, Ty> map
) {

  public ThirScope(ThirScope parent, String name) {
    this(parent, name, new HashMap<>());
  }

  public Ty get(String key) {

    var ptr = this;
    while (ptr != null) {

      final var ty = ptr.map().get(key);
      if (ty != null) {
        return ty;
      }

      ptr = ptr.parent();
    }

    return null;
  }
}
