package org.jetbrains.plugins.scala.lang.findUsages;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

public class ScalaWordsScanner extends DefaultWordsScanner {
  private final Lexer myLexer;
  private final TokenSet myIdentifierTokenSet;
  private final TokenSet myCommentTokenSet;
  private final TokenSet myLiteralTokenSet;
  private final TokenSet mySkipCodeContextTokenSet;

  public ScalaWordsScanner() {
    this(new ScalaLexer(false, null), ScalaTokenTypes.IDENTIFIER_TOKEN_SET, ScalaTokenTypes.COMMENTS_TOKEN_SET, ScalaTokenTypes.STRING_LITERAL_TOKEN_SET, TokenSet.EMPTY);
  }

  public ScalaWordsScanner(final Lexer lexer, final TokenSet identifierTokenSet, final TokenSet commentTokenSet,
                             final TokenSet literalTokenSet, @NotNull TokenSet skipCodeContextTokenSet) {
    super(lexer, identifierTokenSet, commentTokenSet, literalTokenSet, skipCodeContextTokenSet);
    myLexer = lexer;
    myIdentifierTokenSet = identifierTokenSet;
    myCommentTokenSet = commentTokenSet;
    myLiteralTokenSet = literalTokenSet;
    mySkipCodeContextTokenSet = skipCodeContextTokenSet;
  }

  @Override
  public void processWords(@NotNull CharSequence fileText,
                           @NotNull Processor<? super WordOccurrence> processor) {
    myLexer.start(fileText);
    WordOccurrence occurrence = new WordOccurrence(fileText, 0, 0, null); // shared occurrence

    IElementType type;
    while ((type = myLexer.getTokenType()) != null) {
      if (myIdentifierTokenSet.contains(type)) {
        occurrence.init(fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        if (!processor.process(occurrence)) return;
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE, occurrence, false)) return;      }
      else if (myCommentTokenSet.contains(type)) {
        if (!stripWords(processor, fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.COMMENTS,occurrence, false)) return;
      }
      else if (myLiteralTokenSet.contains(type)) {
        boolean mayHaveFileRefs = true; // for indexing string literals as references to property keys and files
        if (!stripWords(processor, fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(),WordOccurrence.Kind.LITERALS,occurrence, mayHaveFileRefs)) return;
      }
      else if (!mySkipCodeContextTokenSet.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE, occurrence, false)) return;
      }
      myLexer.advance();
    }
  }

  @Override
  public int getVersion() {
    return 3;
  }
}
