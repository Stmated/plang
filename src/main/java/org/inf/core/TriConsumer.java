package org.inf.core;

import java.util.Objects;

@FunctionalInterface
public interface TriConsumer<T1, T2, T3> {

  void accept(T1 a, T2 b, T3 c);

  default TriConsumer<T1, T2, T3> andThen(TriConsumer<? super T1, ? super T2, ? super T3> after) {
    Objects.requireNonNull(after);
    return (T1 a, T2 b, T3 c) -> { accept(a, b, c); after.accept(a, b, c); };
  }
}
