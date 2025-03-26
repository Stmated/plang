package org.inf.mir;

import org.inf.mir.Mir.Instr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

record MirScope(
  MirScope parent,
  String name,
  /**
   * TODO: Replace the key from String to a Hir.Dec -- which is a cleaner key and sort of anonymous
   */
  Map<String, List<Instr>> map,
  Map<String, AtomicInteger> idMap
) {

  MirScope(MirScope parent, String name) {
    this(parent, name, new HashMap<>(), new HashMap<>());
  }

  public MirIdentifierId newUniqueId(String name) {
    return newUniqueId(name, null);
  }

  public MirIdentifierId newUniqueId(String name, String label) {

    final var earliestCounter = getEarliestCounter(name);
    if (earliestCounter != null) {
      return new MirIdentifierId(name, label, earliestCounter.incrementAndGet());
    }

    final var id = this.idMap.computeIfAbsent(name, n -> new AtomicInteger(0)).incrementAndGet();
    return new MirIdentifierId(name, label, id);
  }

  private AtomicInteger getEarliestCounter(String name) {

    var pointer = this;
    AtomicInteger earliestCounter = null;
    while (pointer != null) {
      final var counter = pointer.idMap().get(name);
      if (counter != null) {
        earliestCounter = counter;
      }

      pointer = pointer.parent();
    }

    return earliestCounter;
  }

  public void add(String name, Instr instruction) {

    final var e = this.map.computeIfAbsent(
      Objects.requireNonNull(name, "To add scoped instruction it must be named"),
      n -> new ArrayList<>()
    );

    e.add(instruction);
  }

  public Instr get(String name) {

    final var e = this.map.get(name);
    if (e != null) {
      return e.getLast();
    }

    if (parent != null) {
      return parent.get(name);
    }

    return null;
  }

  public MirScope snapshot() {

    final var snapshotParent = (parent == null) ? null : parent.snapshot();
    final var snapshot = new MirScope(snapshotParent, this.name, new HashMap<>(this.map), this.idMap);

    return snapshot;
  }
}
