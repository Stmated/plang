package org.inf.util;

import java.util.function.Function;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArrayUtils {

  public static <T> boolean any(T[] array, Predicate<T> predicate) {
    for (T element : array) {
      if (predicate.test(element)) {
        return true;
      }
    }
    return false;
  }

  public static <T> boolean none(T[] array, Predicate<T> predicate) {
    for (T element : array) {
      if (predicate.test(element)) {
        return false;
      }
    }
    return true;
  }

  public static <T> String[] mapToStrings(T[] array, Function<T, String> mapper) {
    String[] result = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = mapper.apply(array[i]);
    }
    return result;
  }

  public static <T> int count(T[] array, Predicate<T> predicate) {
    var count = 0;
    for (T element : array) {
      if (predicate.test(element)) {
        count++;
      }
    }

    return count;
  }
}
