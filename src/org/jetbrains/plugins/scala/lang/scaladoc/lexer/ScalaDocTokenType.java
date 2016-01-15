package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType;

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
  IElementType DOC_MARKED_ELEMENT = new ScalaDocElementType("DOC_MARKED_ELEMENT");    //
  IElementType DOC_INNER_CODE_TAG = new ScalaDocElementType("DOC_INNER_CODE_TAG");
  IElementType DOC_LINK_CLOSE_TAG = new ScaladocSyntaxElementType("DOC_LINK_CLOSE_TAG", 0);
  IElementType DOC_INNER_CODE = new ScalaDocElementType("DOC_INNER_CODE");
  IElementType DOC_INNER_CLOSE_CODE_TAG = new ScalaDocElementType("DOC_INNER_CLOSE_CODE_TAG");
  IElementType DOC_MACROS = new ScalaDocElementType("DOC_MACROS");
  IElementType DOC_CODE_LINK_VALUE = new ScalaDocElementType("DOC_CODE_LINK_VALUE");
  IElementType DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT = new ScalaDocElementType("DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT");
  IElementType DOC_COMMON_CLOSE_WIKI_TAG = new ScalaDocElementType("DOC_COMMON_CLOSE_WIKI_TAG");

  ScaladocSyntaxElementType DOC_BOLD_TAG = new ScaladocSyntaxElementType("DOC_BOLD_TAG", 1);
  ScaladocSyntaxElementType DOC_ITALIC_TAG = new ScaladocSyntaxElementType("DOC_ITALIC_TAG", 1 << 1);
  ScaladocSyntaxElementType DOC_UNDERLINE_TAG = new ScaladocSyntaxElementType("DOC_UNDERLINE_TAG", 1 << 2);
  ScaladocSyntaxElementType DOC_MONOSPACE_TAG = new ScaladocSyntaxElementType("DOC_MONOSPACE_TAG", 1 << 3);
  ScaladocSyntaxElementType DOC_SUPERSCRIPT_TAG = new ScaladocSyntaxElementType("DOC_SUPERSCRIPT_TAG", 1 << 4);
  ScaladocSyntaxElementType DOC_SUBSCRIPT_TAG = new ScaladocSyntaxElementType("DOC_SUBSCRIPT_TAG", 1 << 5);
  ScaladocSyntaxElementType DOC_LINK_TAG = new ScaladocSyntaxElementType("DOC_LINK_TAG", 1 << 6);
  ScaladocSyntaxElementType DOC_HTTP_LINK_TAG = new ScaladocSyntaxElementType("DOC_HTTP_LINK_TAG", 1 << 7);
  ScaladocSyntaxElementType DOC_HEADER = new ScaladocSyntaxElementType("DOC_HEADER", 1 << 8);
  ScaladocSyntaxElementType VALID_DOC_HEADER = new ScaladocSyntaxElementType("VALID_DOC_HEADER", 1 << 8);


  IElementType DOC_COMMENT_BAD_CHARACTER = new ScalaDocElementType("DOC_COMMENT_BAD_CHARACTER");

  TokenSet ALL_SCALADOC_SYNTAX_ELEMENTS = TokenSet.create(
      ScalaDocTokenType.DOC_BOLD_TAG, ScalaDocTokenType.DOC_ITALIC_TAG, ScalaDocTokenType.DOC_MONOSPACE_TAG,
      ScalaDocTokenType.DOC_SUBSCRIPT_TAG, ScalaDocTokenType.DOC_SUPERSCRIPT_TAG, ScalaDocTokenType.DOC_UNDERLINE_TAG,
      ScalaDocTokenType.DOC_LINK_TAG, ScalaDocTokenType.DOC_LINK_CLOSE_TAG, ScalaDocTokenType.DOC_HTTP_LINK_TAG,
      ScalaDocTokenType.DOC_INNER_CODE_TAG, ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG,
      ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG
  );
  
  TokenSet ALL_SCALADOC_TOKENS = TokenSet.create(
   DOC_COMMENT_START, DOC_COMMENT_END, DOC_COMMENT_DATA, DOC_WHITESPACE, DOC_COMMENT_LEADING_ASTERISKS, DOC_TAG_NAME,
   DOC_INLINE_TAG_START, DOC_INLINE_TAG_END, DOC_TAG_VALUE_TOKEN, DOC_TAG_VALUE_DOT, DOC_TAG_VALUE_COMMA,
   DOC_TAG_VALUE_LPAREN, DOC_TAG_VALUE_RPAREN, DOC_TAG_VALUE_SHARP_TOKEN, DOC_MARKED_ELEMENT, DOC_BOLD_TAG,
   DOC_ITALIC_TAG, DOC_UNDERLINE_TAG, DOC_MONOSPACE_TAG, DOC_SUPERSCRIPT_TAG, DOC_SUBSCRIPT_TAG, DOC_LINK_TAG,
   DOC_HTTP_LINK_TAG, DOC_INNER_CODE_TAG, DOC_INNER_CODE, DOC_HEADER, DOC_LINK_CLOSE_TAG, VALID_DOC_HEADER,
   DOC_INNER_CLOSE_CODE_TAG, DOC_MACROS, DOC_CODE_LINK_VALUE
  );
}
