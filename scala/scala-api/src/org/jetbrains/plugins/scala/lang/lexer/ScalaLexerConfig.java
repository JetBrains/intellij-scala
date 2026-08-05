package org.jetbrains.plugins.scala.lang.lexer;

/**
 * Immutable options which affect Scala tokenization.
 *
 * Project/module settings are resolved by backend adapters before constructing a lexer. Keeping
 * the value here allows the lexer itself to be loaded in every product mode.
 */
public record ScalaLexerConfig(boolean treatDocCommentAsBlockComment) {
  public static final ScalaLexerConfig DEFAULT = new ScalaLexerConfig(false);
}
