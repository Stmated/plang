package org.inf.mir.util;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import org.inf.Inf;
import org.inf.ast.util.SnapshotTestUtils;
import org.inf.mir.Mir;
import org.inf.mir.MirLoweringResult;
import org.inf.mir.model.MirBinaryOperationKind;
import org.inf.mir.model.MirFnParameter;
import org.inf.mir.model.MirFnSignature;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
class MirSExpressionPrinterTest {

  private final MirSExpressionPrinter printer = new MirSExpressionPrinter();

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "arithmetic | var x = 1; x += 2; x",
    "conditional | if (true) then 1 else 2",
    "loop | var a = 0; for (var i = 0; i < 3; i += 1) { a += i; } return a;",
    "calls | val f = (a: int, b: int) => a + b; f(b = 2, a = 1)",
    "array | var a = [1, 2]; a[0] = 3; a[1]",
    "struct | val S = struct { val value: int; }; val s = new heap S { value = 7; }; s.value",
    "conversion | val t: (int16,) = (255u8,); t[0]",
    "varargs | val external = (a: int, ...): int; external(a = 1, 255u8, true, 1.0f)"
  })
  void given__source__when__mir_rendered__then__structural_snapshot(
    final String name, final String code, final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var module = Inf.codeToMir(code);
    final var actual = printer.render(module);
    SnapshotTestUtils.assertMatches(testInfo, snapshot, name, actual);
    printer.render(Inf.codeToMir("0"));
    assertAll(
      () -> assertEquals(actual, printer.render(module)),
      () -> assertEquals(actual, new MirSExpressionPrinter().render(Inf.codeToMir(code)))
    );
  }

  @Test
  void given__unit_return__when__rendered__then__exact_ordered_structure() {
    final var function = function("main", Ty.VOID, false);
    function.entry().terminate(new Mir.Return(Mir.Unit.INSTANCE));

    assertEquals("""
      (MirLoweringResult
        (script (function-ref f0))
        (functions
          (MirFunction
            (id f0)
            (name "main")
            (signature
              (MirFnSignature !vararg
                (parameters)
                (returnType VOID)))
            (locals)
            (blocks
              (MirNode
                (id b0)
                (name "entry_0")
                (instructions)
                (terminator
                  (Return
                    (value (Unit)))))))))""", printer.render(module(function)));
  }

  @ParameterizedTest
  @CsvSource({
    "INFER, INFER",
    "INVALID, INVALID",
    "UNKNOWN, UNKNOWN",
    "DEADEND, DEADEND",
    "VOID, VOID",
    "SHORT, SHORT",
    "USHORT, USHORT",
    "INTEGER, INTEGER",
    "UINTEGER, UINTEGER",
    "LONG, LONG",
    "ULONG, ULONG",
    "CHAR, CHAR",
    "INTEGER_BINARY, INTEGER_BINARY",
    "INTEGER_OCTAL, INTEGER_OCTAL",
    "INTEGER_HEX, INTEGER_HEX",
    "FLOAT16, FLOAT16",
    "FLOAT, FLOAT",
    "DECIMAL, DECIMAL",
    "DOUBLE, DOUBLE",
    "FLOAT64, DOUBLE",
    "BOOLEAN, BOOLEAN",
    "STRING, STRING"
  })
  void given__predefined_type__when__rendered__then__constant_name_is_used(
    final String fieldName, final String expectedName
  ) throws ReflectiveOperationException {
    final var type = assertInstanceOf(Ty.class, Ty.class.getField(fieldName).get(null));
    assertTrue(printer.render(module(function("type", type, false))).contains("(returnType " + expectedName + ")"));
  }

  @Test
  void given__equal_but_distinct_type__when__rendered__then__constant_name_is_used() {
    final var type = Ty.INTEGER.toBuilder().flags(Set.of()).build();
    assertNotSame(Ty.INTEGER, type);
    assertTrue(printer.render(module(function("type", type, false))).contains("(returnType INTEGER)"));
  }

  @ParameterizedTest
  @CsvSource({
    "8, false, 8",
    "16, false, 16",
    "32, false, 32",
    "64, false, 64",
    "8, true, EXPLICIT_8",
    "16, true, EXPLICIT_16",
    "32, true, EXPLICIT_32",
    "64, true, EXPLICIT_64"
  })
  void given__custom_number_with_predefined_width__when__rendered__then__width_name_is_used(
    final int width, final boolean explicit, final String name
  ) {
    final var type = new TyValueNumberInteger((byte) 3, new BitWidth(width, explicit), true, Ty.NO_FLAGS);
    final var actual = printer.render(module(function("width", type, false)));
    assertAll(
      () -> assertTrue(actual.contains("(width %s)".formatted(name))),
      () -> assertTrue(actual.contains("(radix 3)")),
      () -> assertFalse(actual.contains("(BitWidth"))
    );
  }

  @ParameterizedTest
  @CsvSource({
    "7, true",
    "24, true",
    "48, true",
    "128, true",
    "128, false"
  })
  void given__nonmatching_width__when__rendered__then__value_and_explicitness_are_preserved(
    final int width, final boolean explicit
  ) {
    final var type = new TyValueNumberInteger((byte) 3, new BitWidth(width, explicit), true, Ty.NO_FLAGS);
    final var actual = printer.render(module(function("width", type, false)));
    assertAll(
      () -> assertTrue(actual.contains("(BitWidth")),
      () -> assertTrue(actual.contains("(value %d)".formatted(width))),
      () -> assertTrue(actual.contains("(BitWidth%s\n".formatted(explicit ? " explicit" : " !explicit"))),
      () -> assertFalse(actual.contains("(width %d)".formatted(width)))
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__signature_boolean__when__rendered__then__header_tag_is_used(final boolean vararg) {
    final var function = new MirFunction("signature",
      new MirFnSignature(new MirFnParameter[0], vararg, Ty.VOID), true);
    final var actual = printer.render(module(function));
    assertAll(
      () -> assertTrue(actual.contains("(MirFnSignature%s\n".formatted(vararg ? " vararg" : " !vararg"))),
      () -> assertFalse(actual.contains("(vararg "))
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__nested_type_booleans__when__rendered__then__all_fields_use_header_tags(final boolean flag) {
    final var number = new TyValueNumberInteger((byte) 3, new BitWidth(128, flag), flag, Ty.NO_FLAGS);
    final var type = new TyStruct(new TyField[] { new TyField("value", number) }, flag);
    final var actual = printer.render(module(function("type", type, false)));
    assertAll(
      () -> assertTrue(actual.contains("(TyStruct%s\n".formatted(flag ? " tuple" : " !tuple"))),
      () -> assertTrue(actual.contains("(TyValueNumberInteger%s\n".formatted(flag ? " signed" : " !signed"))),
      () -> assertTrue(actual.contains("(BitWidth%s\n".formatted(flag ? " explicit" : " !explicit"))),
      () -> assertFalse(actual.contains("(tuple ")),
      () -> assertFalse(actual.contains("(signed ")),
      () -> assertFalse(actual.contains("(explicit "))
    );
  }

  @ParameterizedTest
  @CsvSource(value = {
    "null,''",
    "true,' optional'",
    "false,' !optional'"
  }, nullValues = "null", ignoreLeadingAndTrailingWhitespace = false)
  void given__mixed_boolean_fields__when__rendered__then__tags_are_grouped_and_nulls_remain_fields(
    final Boolean optional, final String tag
  ) {
    final var type = new BooleanFields(true, "false !second", false, optional);
    final var actual = printer.render(module(function("flags", type, false)));
    assertAll(
      () -> assertTrue(actual.contains("(BooleanFields first !second%s\n".formatted(tag))),
      () -> assertTrue(actual.contains("(text \"false !second\")")),
      () -> assertEquals(optional == null, actual.contains("(optional null)"))
    );
  }

  public record BooleanFields(boolean first, String text, boolean second, Boolean optional) implements Ty {
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "INTEGER",
    "FLOAT",
    "DECIMAL"
  })
  void given__predefined_number_with_different_flags__when__rendered__then__flags_are_not_hidden(
    final String name
  ) throws ReflectiveOperationException {
    final var predefined = assertInstanceOf(TyValueNumber.class, Ty.class.getField(name).get(null));
    final TyValueNumber type = switch (predefined) {
      case final TyValueNumberInteger integer -> integer.toBuilder().flags(Set.of(TyFlags.MUTABLE)).build();
      case final TyValueNumberPrecisioned real -> real.toBuilder().flags(Set.of(TyFlags.MUTABLE)).build();
      case final TyValueNumberScaled decimal ->
        new TyValueNumberScaled(decimal.width(), decimal.scale(), decimal.signed(), Set.of(TyFlags.MUTABLE));
      default -> throw new IllegalArgumentException("Unexpected numeric constant: " + name);
    };
    final var actual = printer.render(module(function("flags", type, false)));
    assertAll(
      () -> assertTrue(actual.contains("(" + type.getClass().getSimpleName())),
      () -> assertTrue(actual.contains("(flags MUTABLE)")),
      () -> assertFalse(actual.contains("(returnType " + name + ")"))
    );
  }

  @Test
  void given__flag_sets_with_different_iteration_orders__when__rendered__then__order_is_deterministic() {
    final var reversed = new LinkedHashSet<TyFlags>();
    reversed.add(TyFlags.IMMUTABLE);
    reversed.add(TyFlags.CONSTANT);
    final var first = Ty.INTEGER.toBuilder().flags(reversed).build();
    final var second = Ty.INTEGER.toBuilder().flags(EnumSet.of(TyFlags.CONSTANT, TyFlags.IMMUTABLE)).build();
    final var actual = printer.render(module(function("flags", first, false)));
    assertAll(
      () -> assertTrue(actual.contains("(flags CONSTANT IMMUTABLE)")),
      () -> assertEquals(actual, printer.render(module(function("flags", second, false))))
    );
  }

  @Test
  void given__eight_bit_constant__when__inspected__then__width_and_char_remain_eight_bits() {
    assertAll(
      () -> assertEquals(8, Ty.BW_8.value()),
      () -> assertFalse(Ty.BW_8.explicit()),
      () -> assertEquals(Ty.BW_8, Ty.CHAR.width()),
      () -> assertNotEquals(Ty.BW_8, Ty.BW_16)
    );
  }

  @Test
  void given__cycles_shared_successors_and_dead_blocks__when__rendered__then__each_block_is_defined_once(
    final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var function = function("graph", Ty.VOID, false);
    final var header = function.newBlock("same");
    final var left = function.newBlock("same");
    final var right = function.newBlock("same");
    final var dead = function.newBlock("dead");
    function.entry().terminate(new Mir.Jump(header));
    header.terminate(new Mir.Branch(new Mir.Constant("true", Ty.BOOLEAN), left, right));
    left.terminate(new Mir.Jump(header));
    right.terminate(new Mir.Branch(new Mir.Constant("false", Ty.BOOLEAN), header, header));
    dead.terminate(new Mir.Unreachable());

    final var actual = assertTimeout(Duration.ofSeconds(1), () -> printer.render(module(function)));
    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, actual);
    assertAll(
      () -> assertEquals(5, actual.lines().filter(line -> line.strip().equals("(MirNode")).count()),
      () -> assertEquals(2, actual.lines().filter(line -> line.strip().equals("(Jump")).count()),
      () -> assertTrue(actual.contains("(pass (block-ref b1))")),
      () -> assertTrue(actual.contains("(fail (block-ref b1))"))
    );
  }

  @Test
  void given__recursive_calls_and_duplicate_names__when__rendered__then__references_use_function_identity(
    final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var script = function("same", Ty.VOID, false);
    final var recursive = function("same", Ty.VOID, false);
    final var external = function("same", Ty.VOID, true);
    script.entry().append(new Mir.Call(null, new Mir.FunctionRef(recursive), recursive.signature(), List.of()));
    script.entry().append(new Mir.Call(null, new Mir.FunctionRef(external), external.signature(), List.of()));
    script.entry().terminate(new Mir.Return(Mir.Unit.INSTANCE));
    recursive.entry().append(new Mir.Call(null, new Mir.FunctionRef(recursive), recursive.signature(), List.of()));
    recursive.entry().terminate(new Mir.Return(Mir.Unit.INSTANCE));
    final var module = new MirLoweringResult(script, List.of(external, script, recursive));

    final var actual = assertTimeout(Duration.ofSeconds(1), () -> printer.render(module));
    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, actual);
    assertAll(
      () -> assertTrue(actual.contains("(script (function-ref f1))")),
      () -> assertEquals(3, actual.lines().filter(line -> line.strip().equals("(MirFunction")).count()),
      () -> assertTrue(actual.contains("(function (function-ref f0))")),
      () -> assertTrue(actual.contains("(function (function-ref f2))")),
      () -> assertTrue(actual.contains("(result null)")),
      () -> assertTrue(actual.contains("(arguments)"))
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__function_blocks__when__rendered__then__derived_getters_are_omitted(final boolean external) {
    final var actual = printer.render(module(function("function", Ty.VOID, external)));
    assertAll(
      () -> assertFalse(actual.contains("(external ")),
      () -> assertFalse(actual.contains("(entry ")),
      () -> assertFalse(actual.contains("(initNode ")),
      () -> assertFalse(actual.contains("(successors ")),
      () -> assertFalse(actual.contains("(operands ")),
      () -> assertEquals(external, actual.contains("(blocks)")),
      () -> assertEquals(!external, actual.contains("(id b0)"))
    );
  }

  @Test
  void given__quoted_scalar_data__when__rendered__then__all_strings_are_escaped() {
    final String text = "quote:\" slash:\\ newline:\n return:\r tab:\t backspace:\b formfeed:\f nul:\0 del:\u007f";
    final String escaped = "\"quote:\\\" slash:\\\\ newline:\\n return:\\r tab:\\t backspace:\\b formfeed:\\f nul:\\u0000 del:\\u007f\"";
    final var function = function(text, Ty.STRING, false);
    function.newLocal(text, Ty.STRING);
    function.entry().terminate(new Mir.Return(new Mir.Constant(text, Ty.STRING)));

    final var actual = printer.render(module(function));
    assertAll(
      () -> assertTrue(actual.contains("(name " + escaped + ")")),
      () -> assertTrue(actual.contains("(content " + escaped + ")")),
      () -> assertEquals(3, actual.lines().filter(line -> line.contains(escaped)).count()),
      () -> assertFalse(actual.chars().anyMatch(character -> Character.isISOControl(character) && character != '\n'))
    );
  }

  @Test
  void given__unterminated_block__when__rendered__then__null_and_empty_are_distinct() {
    final var actual = printer.render(module(function("open", Ty.VOID, false)));
    assertAll(
      () -> assertTrue(actual.contains("(instructions)")),
      () -> assertTrue(actual.contains("(terminator null)")),
      () -> assertFalse(actual.contains("(terminator (Unreachable))")),
      () -> assertThrows(NullPointerException.class, () -> printer.render(null))
    );
  }

  @ParameterizedTest
  @EnumSource(MirBinaryOperationKind.class)
  void given__binary_operation_kind__when__rendered__then__kind_is_preserved(final MirBinaryOperationKind kind) {
    final var function = function("operator", Ty.INTEGER, false);
    final var result = function.newValue(Ty.INTEGER);
    final var constant = new Mir.Constant("1", Ty.INTEGER);
    function.entry().append(new Mir.Binary(result, constant, kind, constant));
    function.entry().terminate(new Mir.Return(result));

    assertTrue(printer.render(module(function)).contains("(kind " + kind.name() + ")"));
  }

  @Test
  void given__remaining_record_shapes__when__rendered__then__all_components_are_preserved(
    final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var union = new TyUnion(new Ty[] { Ty.INTEGER, Ty.STRING });
    final var function = new MirFunction("union", new MirFnSignature(
      new MirFnParameter[] { new MirFnParameter(null, Ty.INTEGER) }, false, union), false);
    final var parameter = function.newValue(Ty.INTEGER);
    final var result = function.newValue(union);
    function.entry().append(new Mir.Parameter(parameter, 0));
    function.entry().append(new Mir.UnionVariant(result, 0, parameter));
    function.entry().terminate(new Mir.Return(result));

    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, printer.render(module(function)));
  }

  @Test
  void given__composite_types__when__rendered__then__full_record_metadata_is_preserved(
    final TestInfo testInfo, final Snapshot snapshot
  ) {
    final var function = function("types", Ty.VOID, false);
    final Ty[] types = {
      Ty.UNKNOWN, new TyIdentifier("Named"), new TyOpaque(), Ty.BOOLEAN, Ty.STRING,
      Ty.INTEGER_BINARY, Ty.FLOAT, Ty.DECIMAL,
      new TyValueNumberInteger((byte) 10, new BitWidth(32, true), false, EnumSet.of(TyFlags.CONSTANT, TyFlags.IMMUTABLE)),
      new TyValueArray(Ty.INTEGER, null), new TyValueArray(Ty.INTEGER, 0),
      new TyUninitialized<>(Ty.INTEGER),
      new TyPointer<>(Ty.INTEGER, TyPointerAddressSpace.CUDA_GLOBAL),
      new TyPointerExplicit(Ty.INTEGER),
      new TyUnion(new Ty[] { Ty.INTEGER, Ty.STRING }),
      new TyFn(new TyParam[] { new TyParam("p", Ty.INTEGER) }, true, Ty.VOID),
      new TyStruct(new TyField[] { new TyField("field", Ty.STRING) }, false),
      new TyStruct(new TyField[0], true)
    };
    for (final var type : types) {
      function.newLocal("type", type);
    }

    SnapshotTestUtils.assertMatches(testInfo, snapshot, null, printer.render(module(function)));
  }

  @Test
  void given__recursive_and_shared_types__when__rendered__then__only_active_ancestors_are_references() {
    final var fields = new TyField[2];
    final var type = new TyStruct(fields, false);
    fields[0] = new TyField("next", new TyPointer<>(type));
    fields[1] = new TyField("other", Ty.STRING);
    final var function = function("recursive-type", Ty.VOID, false);
    function.newLocal("first", type);
    function.newLocal("second", type);

    final var actual = assertTimeout(Duration.ofSeconds(1), () -> printer.render(module(function)));
    assertAll(
      () -> assertEquals(2, actual.lines().filter(line -> line.contains("(inner (recursive 1))")).count()),
      () -> assertEquals(2, actual.lines().filter(line -> line.strip().equals("(TyStruct !tuple")).count()),
      () -> assertEquals(2, actual.lines().filter(line -> line.contains("(name \"other\")")).count()),
      () -> assertEquals(actual, printer.render(module(function)))
    );
  }

  @ParameterizedTest
  @ValueSource(booleans = {
    false,
    true
  })
  void given__unowned_graph_reference__when__rendered__then__explicit_error(final boolean functionReference) {
    final var script = function("main", Ty.VOID, false);
    final var foreign = function("foreign", Ty.VOID, false);
    if (functionReference) {
      script.entry().append(new Mir.Call(null, new Mir.FunctionRef(foreign), foreign.signature(), List.of()));
    } else {
      script.entry().terminate(new Mir.Jump(foreign.entry()));
    }

    final var error = assertThrows(IllegalArgumentException.class, () -> printer.render(module(script)));
    assertTrue(error.getMessage().contains(functionReference ? "Unowned MIR function-ref" : "Unowned MIR block-ref"));
  }

  @Test
  void given__duplicate_function_ownership__when__rendered__then__explicit_error() {
    final var script = function("main", Ty.VOID, false);
    final var error = assertThrows(IllegalArgumentException.class,
      () -> printer.render(new MirLoweringResult(script, List.of(script, script))));
    assertTrue(error.getMessage().contains("Duplicate MIR function"));
  }

  @Test
  void given__different_finite_types__when__rendered__then__inner_differences_are_not_truncated() {
    Ty integer = Ty.INTEGER;
    Ty string = Ty.STRING;
    for (int i = 0; i < 20; i++) {
      integer = new TyPointer<>(integer);
      string = new TyPointer<>(string);
    }
    final var first = function("deep", integer, false);
    final var second = function("deep", string, false);
    assertNotEquals(printer.render(module(first)), printer.render(module(second)));
  }

  private static MirFunction function(final String name, final Ty result, final boolean external) {
    return new MirFunction(name, new MirFnSignature(new MirFnParameter[0], false, result), external);
  }

  private static MirLoweringResult module(final MirFunction function) {
    return new MirLoweringResult(function, List.of(function));
  }
}
