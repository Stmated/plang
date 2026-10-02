package org.inf.lexer;

import org.inf.parser.InfTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

class CommentFilteringIteratorTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "/* before */ f /* between */ x /* after */",
    "// before\nf // between\nx // after\n",
    "/* a */ // b\nf /* c */ /* d */ x"
  })
  void given__comment_tokens__when__filtered__then__original_significant_tokens_are_preserved(final String code) throws Exception {
    final var tokens = new ArrayList<Token>();
    try (final var lexer = new InfLexer(InfTestUtil.stringToStream(code))) {
      lexer.forEachRemaining(tokens::add);
    }
    final var expected = tokens.stream().filter(token -> !token.type().isComment()).toList();
    final var filter = new CommentFilteringIterator(tokens.iterator());
    final var actual = new ArrayList<Token>();
    while (filter.hasNext()) {
      Assertions.assertEquals(true, filter.hasNext());
      actual.add(filter.next());
    }
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, actual),
      () -> Assertions.assertEquals(false, filter.hasNext()),
      () -> Assertions.assertThrows(NoSuchElementException.class, filter::next)
    );
    for (var i = 0; i < expected.size(); i++) {
      Assertions.assertSame(expected.get(i), actual.get(i));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "// only a comment",
    "/* comment */ // another\n"
  })
  void given__no_significant_tokens__when__filtered__then__empty_iterator(final String code) throws Exception {
    try (final var lexer = new InfLexer(InfTestUtil.stringToStream(code))) {
      final var filter = new CommentFilteringIterator(lexer);
      Assertions.assertAll(
        () -> Assertions.assertEquals(false, filter.hasNext()),
        () -> Assertions.assertThrows(NoSuchElementException.class, filter::next)
      );
    }
  }

  @Test
  void given__next_without_has_next__when__filtered__then__comments_are_skipped() {
    final var value = new Token(TokenType.IDENTIFIER, 14, 15, "x");
    final var filter = new CommentFilteringIterator(List.of(
      new Token(TokenType.COMMENT_MULTI_LINE, 0, 13, "comment"), value
    ).iterator());
    Assertions.assertSame(value, filter.next());
    Assertions.assertThrows(NoSuchElementException.class, filter::next);
  }
}
