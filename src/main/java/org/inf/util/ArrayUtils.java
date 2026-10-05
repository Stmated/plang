package org.inf.util;

import lombok.experimental.UtilityClass;

import java.util.function.Function;
import java.util.function.Predicate;

@UtilityClass
public class ArrayUtils {

  public static <T> boolean any(final T[] array, final Predicate<T> predicate) {
    for (final T element : array) {
      if (predicate.test(element)) {
        return true;
      }
    }
    return false;
  }

  public static <T> boolean none(final T[] array, final Predicate<T> predicate) {
    for (final T element : array) {
      if (predicate.test(element)) {
        return false;
      }
    }
    return true;
  }

  public static <T> String[] mapToStrings(final T[] array, final Function<T, String> mapper) {
    final var result = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = mapper.apply(array[i]);
    }
    return result;
  }

  public static <T> int count(final T[] array, final Predicate<T> predicate) {
    var count = 0;
    for (final T element : array) {
      if (predicate.test(element)) {
        count++;
      }
    }

    return count;
  }
}
