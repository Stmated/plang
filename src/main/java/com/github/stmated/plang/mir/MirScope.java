package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirAssignment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

record MirScope(
  MirScope parent,
  String name,
  Map<String, List<MirAssignment>> map,
  Map<String, AtomicInteger> idMap
) {

  MirScope(MirScope parent, String name) {
    this(parent, name, new HashMap<>(), new HashMap<>());
  }

  MirScope(MirScope parent, String name, Map<String, List<MirAssignment>> map, Map<String, AtomicInteger> idMap) {
    this.parent = parent;
    this.name = name;
    this.map = map;
    this.idMap = idMap;
  }

  public MirIdentifierId newUniqueId(String name) {

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
      return new MirIdentifierId(name, earliest_counter.incrementAndGet());
    }

    final var id = this.idMap.computeIfAbsent(name, n -> new AtomicInteger(0)).incrementAndGet();
    return new MirIdentifierId(name, id);
  }

  public void add(MirAssignment operand) {

    final var e = this.map.computeIfAbsent(operand.iid().name(), n -> new ArrayList<>());
    e.add(operand);
  }

  public MirAssignment get(String name) {

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
