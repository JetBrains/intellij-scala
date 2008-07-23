package org.jetbrains.plugins.scala.lang.scaladoc.highlighter;

import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter;

import java.util.Map;
import java.util.HashMap;

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.07.2008
 */
public class ScalaDocSyntaxHighlighter extends SyntaxHighlighterBase implements ScalaDocTokenType {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

  @NotNull
  public Lexer getHighlightingLexer() {
    return  new ScalaDocLexer();
  }

  static final TokenSet tCOMMENT_TAGS = TokenSet.create(
     DOC_TAG_NAME 
  );

  static {
    fillMap(ATTRIBUTES, tCOMMENT_TAGS, DefaultHighlighter.SCALA_DOC_TAG);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
