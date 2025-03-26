package org.inf.llvm.lowering;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Cache<K, V> {

  private final Map<K, V> map = new HashMap<>();

  public V get(K key) {
    return map.get(key);
  }

  public V getOrCompute(K key, Supplier<V> supplier) {
    return map.computeIfAbsent(key, k -> supplier.get());
  }

  public void put(K key, V value) {
    map.put(key, value);
  }
}
