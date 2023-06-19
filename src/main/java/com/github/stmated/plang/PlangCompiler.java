package com.github.stmated.plang;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
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

    final var lexer = new PlangLexer(charStream);

    final var tokenStream = new CommonTokenStream(lexer);
    final var parser = new PlangParser(tokenStream);
    parser.addParseListener(new PlangParserBaseListener() {

      @Override
      public void enterEveryRule(ParserRuleContext ctx) {
        super.enterEveryRule(ctx);
      }

      @Override
      public void enterRoot(PlangParser.RootContext ctx) {
        super.enterRoot(ctx);
      }
    });

    final var rootContext = parser.root();

    try (var os = streamCreator.create(file)) {

      final var path = new ArrayList<RuleNode>();
      final var visitor = new PlangParserBaseVisitor<Void>() {

        @Override
        public Void visitChildren(RuleNode node) {
          System.out.println(" ".repeat(node.getRuleContext().depth()) + node.getClass().getSimpleName() + " - " + node.getText());
          try {
            path.add(node);
            return super.visitChildren(node);
          } finally {
            path.remove(node);
          }
        }

        @Override
        public Void visitDotExpression(PlangParser.DotExpressionContext ctx) {
          return super.visitDotExpression(ctx);
        }

        @Override
        public Void visitRoot(PlangParser.RootContext ctx) {
          return super.visitRoot(ctx);
        }

        @Override
        public Void visitIdentifier(PlangParser.IdentifierContext ctx) {
          return super.visitIdentifier(ctx);
        }

        @Override
        public Void visitTypeName(PlangParser.TypeNameContext ctx) {
          return super.visitTypeName(ctx);
        }

        @Override
        public Void visitErrorNode(ErrorNode node) {

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

          throw new RuntimeException(file + ": " + errorMessage.toString());
        }
      };

      visitor.visit(rootContext);
    }
  }
}
