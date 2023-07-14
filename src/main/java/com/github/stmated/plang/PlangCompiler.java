package com.github.stmated.plang;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.RuleNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;

public class PlangCompiler {

  private static final PathMatcher plangPatchMatcher
      = FileSystems.getDefault().getPathMatcher("glob:*.plang");

  public void compileDirectory(Path sourceDirectory, StreamCreator streamCreator) throws IOException {

    final var paths = new ArrayList<Path>();
    PlangCompiler.find(sourceDirectory, paths);

    for (final var path : paths) {
      this.compile(path, streamCreator);
    }
  }

  public static void find(Path fileOrDirectory, Collection<Path> target) throws IOException {

    if (Files.isDirectory(fileOrDirectory)) {

      try (final var children = Files.list(fileOrDirectory)) {
        for (final var child : children.toList()) {
          PlangCompiler.find(child, target);
        }
      }

    } else {
      if (plangPatchMatcher.matches(fileOrDirectory.getFileName())) {
        target.add(fileOrDirectory);
      }
    }
  }

  private void compile(Path file, StreamCreator streamCreator) throws IOException {

    final var charStream = CharStreams.fromPath(file, StandardCharsets.UTF_8);

    final var lexer = new PlangLexerJava(charStream);
    lexer.addErrorListener(new BaseErrorListener() {

      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        throw new RuntimeException("Syntax Error: " + offendingSymbol + " @ " + line + ":" + charPositionInLine + ": " + msg, e);
      }
    });

    final var tokenStream = new CommonTokenStream(lexer);
    final var parser = new PlangParserJava(tokenStream);
    parser.addErrorListener(new BaseErrorListener() {

      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        throw new RuntimeException("Syntax Error: " + offendingSymbol + " @ " + line + ":" + charPositionInLine + ": " + msg, e);
      }
    });

    final var path = new ArrayList<RuleNode>();
    parser.addParseListener(new PlangParserJavaBaseListener() {

      @Override
      public void enterEveryRule(ParserRuleContext node) {

        System.out.println(" ".repeat(node.getRuleContext().depth()) + node.getClass().getSimpleName() + " - " + node.getText());
        try {
          path.add(node);
          super.enterEveryRule(node);
        } finally {
          path.remove(node);
        }
      }

      @Override
      public void visitErrorNode(ErrorNode node) {
        super.visitErrorNode(node);

        final var errorMessage = new StringBuilder();
        final var pathStrings = path.stream()
            .map(it -> {
              final var si = it.getSourceInterval();
              final var rc = it.getRuleContext();

              return rc.depth() + ": " + it.getClass().getSimpleName() + ": " + si.a + ":" + si.b;
            })
            .toList();

        final var pathString = String.join(System.lineSeparator(), pathStrings);

        errorMessage
            .append(System.lineSeparator())
            .append("Path: ").append(System.lineSeparator())
            .append(pathString).append(System.lineSeparator());

        errorMessage.append("ErrorNode: ").append(node.toString()).append(System.lineSeparator());

        throw new RuntimeException(file + ": " + errorMessage);
      }
    });

    final var rootContext = parser.program();

    try (var os = streamCreator.create(file)) {

      final var visitor = new PlangParserJavaBaseVisitor<Void>() {

        @Override
        public Void visitErrorNode(ErrorNode node) {
          throw new RuntimeException(file + ": " + node.toString());
        }
      };

      visitor.visit(rootContext);
    }
  }
}
