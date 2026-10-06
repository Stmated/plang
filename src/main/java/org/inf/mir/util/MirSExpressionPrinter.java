package org.inf.mir.util;

import org.inf.mir.Mir;
import org.inf.mir.MirLoweringResult;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.BitWidth;
import org.inf.ty.Ty;
import org.inf.ty.TyValueNumber;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Structural MIR snapshots: `new MirSExpressionPrinter().render(Inf.codeToMir(code))`.
///
/// Functions and blocks are defined once in ownership order, including unreachable blocks.
/// Function IDs are module-scoped; block IDs are function-scoped. Edges and function operands
/// are references, not recursive expansions. Non-boolean record components (including types) are printed
/// in declaration order using public accessors, without inspecting implementation fields.
/// Boolean values are header symbols: `field` for true and `!field` for false.
/// This is the printer's convention, not built-in S-expression negation.
/// Types and bit widths matching public constants in `Ty` use their field names instead,
/// with `BW_` omitted for widths. Equal aliases use the first name alphabetically.
/// Unmatched values retain their complete structure, including number flags and width explicitness.
/// Derived getters are omitted: an empty block list denotes an external function;
/// otherwise its first block is the entry.
/// Recursive records refer to their ancestor's zero-based depth in the active record path.
/// Nulls and empty collections remain distinct. Rendering neither verifies nor mutates MIR;
/// references outside the module or current function are rejected rather than omitted.
public final class MirSExpressionPrinter {

  private static final List<NamedConstant> NAMED_CONSTANTS = namedConstants();

  public String render(final MirLoweringResult module) {
    return new Printer(Objects.requireNonNull(module)).render(module);
  }

  private static List<NamedConstant> namedConstants() {
    final var constants = new ArrayList<NamedConstant>();
    final var fields = Ty.class.getFields();
    Arrays.sort(fields, Comparator.comparing(Field::getName));
    for (final var field : fields) {
      final var name = field.getName();
      final var underscoreIndex = name.indexOf('_');
      final var lastIndex = name.lastIndexOf('_');

      // Remove any short prefixes for the display name, such as `BW_`
      // `BW_EXPLICIT_16` becomes `EXPLICIT_16`.
      final String displayName;
      if (underscoreIndex != -1) {

        if (underscoreIndex != lastIndex) {
          displayName = name.substring(underscoreIndex + 1);
        } else {
          if (underscoreIndex < 4) {
            displayName = name.substring(underscoreIndex + 1);
          } else {
            displayName = name;
          }
        }
      } else {
        displayName = name;
      }

      try {
        constants.add(new NamedConstant(field.get(null), displayName));
      } catch (final IllegalAccessException exception) {
        throw new IllegalStateException("Cannot read Ty constant: " + name, exception);
      }
    }
    return Collections.unmodifiableList(constants);
  }

  private static String constantName(final Object value) {
    for (final var constant : NAMED_CONSTANTS) {
      if (constant.matches(value)) {
        return constant.name();
      }
    }
    return null;
  }

  private record NamedConstant(Object value, String name) {

    private boolean matches(final Object candidate) {
      if (value instanceof final TyValueNumber expected && candidate instanceof final TyValueNumber actual) {
        // Some numeric equals implementations omit flags; snapshots must not hide those differences.
        return expected.equals(actual) && Objects.equals(expected.flags(), actual.flags());
      }
      return value.equals(candidate);
    }
  }

  private static final class Printer {

    private final StringBuilder output = new StringBuilder();
    private final Map<MirFunction, String> functionIds = new IdentityHashMap<>();
    private final Map<MirNode, String> blockIds = new IdentityHashMap<>();
    private final Map<Record, Integer> activeRecords = new IdentityHashMap<>();

    private Printer(final MirLoweringResult module) {
      for (final var function : module.functions()) {
        if (functionIds.containsKey(function)) {
          throw new IllegalArgumentException("Duplicate MIR function in module: " + function.name());
        }
        functionIds.put(function, "f%d".formatted(functionIds.size()));
      }
    }

    private String render(final MirLoweringResult module) {
      output.append("(MirLoweringResult");
      field("script", module.script(), 1);
      line(1);
      output.append("(functions");
      for (final var function : module.functions()) {
        line(2);
        function(function, 2);
      }
      return output.append("))").toString();
    }

    private void function(final MirFunction function, final int depth) {
      blockIds.clear();
      for (final var block : function.blocks()) {
        blockIds.put(block, "b%d".formatted(blockIds.size()));
      }
      output.append("(MirFunction");
      id(functionIds.get(function), depth + 1);
      field("name", function.name(), depth + 1);
      field("signature", function.signature(), depth + 1);
      field("locals", function.locals(), depth + 1);
      line(depth + 1);
      output.append("(blocks");
      for (final var block : function.blocks()) {
        line(depth + 2);
        output.append("(MirNode");
        id(blockIds.get(block), depth + 3);
        field("name", block.name(), depth + 3);
        field("instructions", block.instructions(), depth + 3);
        field("terminator", block.terminator(), depth + 3);
        output.append(')');
      }
      output.append("))");
    }

    private void field(final String name, final Object value, final int depth) {
      if (value instanceof final Boolean bool) {
        output.append(' ');
        if (!bool) {
          output.append('!');
        }
        output.append(name);
        return;
      }
      line(depth);
      output.append('(').append(name);
      if (value instanceof final List<?> items) {
        for (final var item : items) {
          item(item, depth + 1);
        }
      } else if (value instanceof final Set<?> items) {
        items.stream().map(Printer::enumValue).sorted(Comparator.comparing(Enum::name))
          .forEach(item -> item(item, depth + 1));
      } else if (value != null && value.getClass().isArray()) {
        for (int i = 0; i < Array.getLength(value); i++) {
          item(Array.get(value, i), depth + 1);
        }
      } else {
        item(value, depth + 1);
      }
      output.append(')');
    }

    private void item(final Object value, final int depth) {
      final var name = constantName(value);
      if (name != null) {
        output.append(' ').append(name);
        return;
      }
      if (value instanceof final Record record && record.getClass().getRecordComponents().length > 0
        && !activeRecords.containsKey(record)) {
        line(depth);
      } else {
        output.append(' ');
      }
      value(value, depth);
    }

    private static Enum<?> enumValue(final Object value) {
      if (value instanceof final Enum<?> enumeration) {
        return enumeration;
      }
      throw new IllegalArgumentException("Unsupported MIR snapshot set element: "
        + (value == null ? "null" : value.getClass().getName()));
    }

    private void value(final Object value, final int depth) {
      switch (value) {
        case null -> output.append("null");
        case final String string -> quote(string);
        case final Boolean bool -> output.append(bool);
        case final Number number -> output.append(number);
        case final Mir.Unit ignored -> output.append("(Unit)");
        case final Enum<?> enumeration -> output.append(enumeration.name());
        case final MirFunction function -> reference("function-ref", functionIds, function, function.name());
        case final MirNode block -> reference("block-ref", blockIds, block, block.name());
        case final Record record -> record(record, depth);
        default -> throw new IllegalArgumentException("Unsupported MIR snapshot value: " + value.getClass().getName());
      }
    }

    private <T> void reference(final String kind, final Map<T, String> ids, final T target, final String name) {
      final var id = ids.get(target);
      if (id == null) {
        throw new IllegalArgumentException("Unowned MIR %s: %s".formatted(kind, name));
      }
      output.append('(').append(kind).append(' ').append(id).append(')');
    }

    private void record(final Record record, final int depth) {
      final var ancestor = activeRecords.get(record);
      if (ancestor != null) {
        output.append("(recursive ").append(ancestor).append(')');
        return;
      }
      activeRecords.put(record, activeRecords.size());
      try {
        output.append('(').append(record.getClass().getSimpleName());
        final var components = record.getClass().getRecordComponents();
        final var values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
          final var component = components[i];
          try {
            values[i] = component.getAccessor().invoke(record);
          } catch (final IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot render MIR record component %s.%s"
              .formatted(record.getClass().getName(), component.getName()), exception);
          }
          if (values[i] instanceof Boolean) {
            field(component.getName(), values[i], depth + 1);
          }
        }
        for (int i = 0; i < components.length; i++) {
          if (!(values[i] instanceof Boolean)) {
            field(components[i].getName(), values[i], depth + 1);
          }
        }
        output.append(')');
      } finally {
        activeRecords.remove(record);
      }
    }

    private void line(final int depth) {
      output.append('\n').repeat("  ", depth);
    }

    private void id(final String id, final int depth) {
      line(depth);
      output.append("(id ").append(id).append(')');
    }

    private void quote(final String value) {
      output.append('"');
      for (int i = 0; i < value.length(); i++) {
        final char character = value.charAt(i);
        switch (character) {
          case '\\' -> output.append("\\\\");
          case '"' -> output.append("\\\"");
          case '\n' -> output.append("\\n");
          case '\r' -> output.append("\\r");
          case '\t' -> output.append("\\t");
          case '\b' -> output.append("\\b");
          case '\f' -> output.append("\\f");
          default -> {
            if (Character.isISOControl(character)) {
              output.append("\\u%04x".formatted((int) character));
            } else {
              output.append(character);
            }
          }
        }
      }
      output.append('"');
    }
  }
}
