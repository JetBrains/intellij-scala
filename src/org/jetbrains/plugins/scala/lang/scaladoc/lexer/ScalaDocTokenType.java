package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public interface ScalaDocTokenType {
  IElementType DOC_COMMENT_START = new ScalaDocElementType("DOC_COMMENT_START");
  IElementType DOC_COMMENT_END = new ScalaDocElementType("DOC_COMMENT_END");
  IElementType DOC_COMMENT_DATA = new ScalaDocElementType("DOC_COMMENT_DATA");
  IElementType DOC_WHITESPACE = new ScalaDocElementType("DOC_WHITESPACE");
  IElementType DOC_COMMENT_LEADING_ASTERISKS = new ScalaDocElementType("DOC_COMMENT_LEADING_ASTERISKS");
  IElementType DOC_TAG_NAME = new ScalaDocElementType("DOC_TAG_NAME");
  IElementType DOC_INLINE_TAG_START = new ScalaDocElementType("DOC_INLINE_TAG_START");
  IElementType DOC_INLINE_TAG_END = new ScalaDocElementType("DOC_INLINE_TAG_END");

  IElementType DOC_TAG_VALUE_TOKEN = new ScalaDocElementType("DOC_TAG_VALUE_TOKEN");
  IElementType DOC_TAG_VALUE_DOT = new ScalaDocElementType("DOC_TAG_VALUE_DOT");
  IElementType DOC_TAG_VALUE_COMMA = new ScalaDocElementType("DOC_TAG_VALUE_COMMA");
  IElementType DOC_TAG_VALUE_LPAREN = new ScalaDocElementType("DOC_TAG_VALUE_LPAREN");
  IElementType DOC_TAG_VALUE_RPAREN = new ScalaDocElementType("DOC_TAG_VALUE_RPAREN");
  IElementType DOC_TAG_VALUE_LT = new ScalaDocElementType("DOC_TAG_VALUE_LT");
  IElementType DOC_TAG_VALUE_GT = new ScalaDocElementType("DOC_TAG_VALUE_GT");
  IElementType DOC_TAG_VALUE_SHARP_TOKEN = new ScalaDocElementType("DOC_TAG_VALUE_SHARP_TOKEN");

  IElementType DOC_COMMENT_BAD_CHARACTER = new ScalaDocElementType("DOC_COMMENT_BAD_CHARACTER");

  TokenSet ALL_SCALADOC_TOKENS = TokenSet.create(
   DOC_COMMENT_START, DOC_COMMENT_END, DOC_COMMENT_DATA, DOC_WHITESPACE, DOC_COMMENT_LEADING_ASTERISKS, DOC_TAG_NAME,
   DOC_INLINE_TAG_START, DOC_INLINE_TAG_END, DOC_TAG_VALUE_TOKEN, DOC_TAG_VALUE_DOT, DOC_TAG_VALUE_COMMA,
   DOC_TAG_VALUE_LPAREN, DOC_TAG_VALUE_RPAREN, DOC_TAG_VALUE_SHARP_TOKEN
  );
}
