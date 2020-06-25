package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType;

// TODO: rename to ScalaDocTokenTypes (with S in the end)
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
  IElementType DOC_TAG_VALUE_SHARP_TOKEN = new ScalaDocElementType("DOC_TAG_VALUE_SHARP_TOKEN");
  IElementType DOC_MARKED_ELEMENT = new ScalaDocElementType("DOC_MARKED_ELEMENT");    //

  IElementType DOC_INNER_CODE_TAG = new ScalaDocElementType("DOC_INNER_CODE_TAG"); // TODO: rename to DOC_INNER_CODE_START
  IElementType DOC_INNER_CODE = new ScalaDocElementType("DOC_INNER_CODE");
  IElementType DOC_HTTP_LINK_VALUE = new ScalaDocElementType("DOC_HTTP_LINK_VALUE");
  IElementType DOC_INNER_CLOSE_CODE_TAG = new ScalaDocElementType("DOC_INNER_CLOSE_CODE_TAG"); // TODO: rename to DOC_INNER_CLOSE_END

  IElementType DOC_CODE_LINK_VALUE = new ScalaDocElementType("DOC_CODE_LINK_VALUE");

  IElementType DOC_MACROS = new ScalaDocElementType("DOC_MACROS");

  // meta element types, only used during highlighting
  IElementType DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT = new ScalaDocElementType("DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT");
  IElementType DOC_COMMON_CLOSE_WIKI_TAG = new ScalaDocElementType("DOC_COMMON_CLOSE_WIKI_TAG");

  IElementType DOC_LIST_ITEM_HEAD = new ScalaDocElementType("DOC_LIST_ITEM_HEAD");

  ScalaDocSyntaxElementType DOC_BOLD_TAG = new ScalaDocSyntaxElementType("DOC_BOLD_TAG", 1);
  ScalaDocSyntaxElementType DOC_ITALIC_TAG = new ScalaDocSyntaxElementType("DOC_ITALIC_TAG", 1 << 1);
  ScalaDocSyntaxElementType DOC_UNDERLINE_TAG = new ScalaDocSyntaxElementType("DOC_UNDERLINE_TAG", 1 << 2);
  ScalaDocSyntaxElementType DOC_MONOSPACE_TAG = new ScalaDocSyntaxElementType("DOC_MONOSPACE_TAG", 1 << 3);
  ScalaDocSyntaxElementType DOC_SUPERSCRIPT_TAG = new ScalaDocSyntaxElementType("DOC_SUPERSCRIPT_TAG", 1 << 4);
  ScalaDocSyntaxElementType DOC_SUBSCRIPT_TAG = new ScalaDocSyntaxElementType("DOC_SUBSCRIPT_TAG", 1 << 5);
  ScalaDocSyntaxElementType DOC_LINK_TAG = new ScalaDocSyntaxElementType("DOC_LINK_TAG", 1 << 6); // rename to DOC_LINK_START_TAG
  ScalaDocSyntaxElementType DOC_HTTP_LINK_TAG = new ScalaDocSyntaxElementType("DOC_HTTP_LINK_TAG", 1 << 7);
  ScalaDocSyntaxElementType DOC_LINK_CLOSE_TAG = new ScalaDocSyntaxElementType("DOC_LINK_CLOSE_TAG", 0); // TODO: rename to DOC_LINK_END
  // TODO: something is wrong with header parsing.
  //  everything between `===` ===header =content= 42=== should be a content `header =content= 42`
  //  and inner `=` should not be parsed as inner DOC_HEADER
  //  DOC_HEADER should be renamed to DOC_HEADER_END after this is fixed
  //  VALID_DOC_HEADER should be renamed to DOC_HEADER_START after this is fixed
  ScalaDocSyntaxElementType VALID_DOC_HEADER = new ScalaDocSyntaxElementType("VALID_DOC_HEADER", 1 << 8);
  ScalaDocSyntaxElementType DOC_HEADER = new ScalaDocSyntaxElementType("DOC_HEADER", 1 << 8);

  IElementType DOC_COMMENT_BAD_CHARACTER = new ScalaDocElementType("DOC_COMMENT_BAD_CHARACTER");

  TokenSet ALL_SCALADOC_SYNTAX_ELEMENTS = TokenSet.create(
          ScalaDocTokenType.DOC_BOLD_TAG, ScalaDocTokenType.DOC_ITALIC_TAG, ScalaDocTokenType.DOC_MONOSPACE_TAG,
          ScalaDocTokenType.DOC_SUBSCRIPT_TAG, ScalaDocTokenType.DOC_SUPERSCRIPT_TAG, ScalaDocTokenType.DOC_UNDERLINE_TAG,
          ScalaDocTokenType.DOC_LINK_TAG, ScalaDocTokenType.DOC_LINK_CLOSE_TAG, ScalaDocTokenType.DOC_HTTP_LINK_TAG,
          ScalaDocTokenType.DOC_INNER_CODE_TAG, ScalaDocTokenType.DOC_INNER_CLOSE_CODE_TAG,
          ScalaDocTokenType.VALID_DOC_HEADER, ScalaDocTokenType.DOC_HEADER,
          ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG
  );

  TokenSet ALL_SCALADOC_TOKENS = TokenSet.orSet(ALL_SCALADOC_SYNTAX_ELEMENTS, TokenSet.create(
          DOC_COMMENT_START, DOC_COMMENT_END, DOC_COMMENT_DATA, DOC_WHITESPACE, DOC_COMMENT_LEADING_ASTERISKS, DOC_TAG_NAME,
          DOC_INLINE_TAG_START, DOC_INLINE_TAG_END, DOC_TAG_VALUE_TOKEN, DOC_TAG_VALUE_DOT, DOC_TAG_VALUE_COMMA,
          DOC_TAG_VALUE_LPAREN, DOC_TAG_VALUE_RPAREN, DOC_TAG_VALUE_SHARP_TOKEN, DOC_MARKED_ELEMENT,
          DOC_INNER_CODE, DOC_MACROS, DOC_CODE_LINK_VALUE, DOC_HTTP_LINK_VALUE,
          DOC_LIST_ITEM_HEAD
  ));
}
