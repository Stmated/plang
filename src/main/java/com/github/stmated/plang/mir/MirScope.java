package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirInstr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

record MirScope(
  MirScope parent,
  String name,
  Map<String, List<MirInstr>> map,
  Map<String, AtomicInteger> idMap
) {

  MirScope(MirScope parent, String name) {
    this(parent, name, new HashMap<>(), new HashMap<>());
  }

  MirScope(MirScope parent, String name, Map<String, List<MirInstr>> map, Map<String, AtomicInteger> idMap) {
    this.parent = parent;
    this.name = name;
    this.map = map;
    this.idMap = idMap;
  }

  public MirIdentifierId newUniqueId(String name) {
    return newUniqueId(name, null);
  }

  public MirIdentifierId newUniqueId(String name, String label) {

    var pointer = this;
    AtomicInteger earliest_counter = null;
    while (pointer != null) {
      final var counter = pointer.idMap().get(name);
      if (counter != null) {
        earliest_counter = counter;
      }

      pointer = pointer.parent();
    }

    if (earliest_counter != null) {
      return new MirIdentifierId(name, label, earliest_counter.incrementAndGet());
    }

    final var id = this.idMap.computeIfAbsent(name, n -> new AtomicInteger(0)).incrementAndGet();
    return new MirIdentifierId(name, label, id);
  }

  public void add(MirInstr instruction) {

    final var iid = Objects.requireNonNull(
      instruction.name(),
      "To add a scoped instruction, it must be identifiable"
    );

    final var e = this.map.computeIfAbsent(
      Objects.requireNonNull(iid.name(), "To add scoped instruction it must be named"),
      n -> new ArrayList<>()
    );

    e.add(instruction);
  }

  public MirInstr get(String name) {

    final var e = this.map.get(name);
    if (e != null) {
      return e.getLast();
    }

    if (parent != null) {
      return parent.get(name);
    }

    return null;
  }
}
