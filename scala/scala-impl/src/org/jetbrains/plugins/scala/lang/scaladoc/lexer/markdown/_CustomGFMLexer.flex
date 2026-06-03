/*
 * This file is copied from https://github.com/JetBrains/markdown/blob/master/src/commonMain/kotlin/org/intellij/markdown/flavours/gfm/lexer/gfm.flex
 */
package org.jetbrains.plugins.scala.lang.scaladoc.lexer.markdown;

import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;
import org.intellij.markdown.IElementType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

%%

%class _CustomGFMLexer
%unicode
%public

%type IElementType

%implements CustomMarkdownLexer

%{
  public _CustomGFMLexer() {
    this(null);
  }

  private static class Token extends MarkdownTokenTypes {}

  private List<Integer> stateStack = new ArrayList<Integer>();

  private boolean isHeader = false;

  private int wikilinkOpenLength = 0;

  private int codeSpanBacktickslength = 0;

  private ParseDelimited parseDelimited = new ParseDelimited();

  private static class ParseDelimited {
    char exitChar = 0;
    IElementType returnType = null;
    boolean inlinesAllowed = true;
  }

  private static class LinkDef {
    boolean wasUrl;
    boolean wasParen;
  }

  private static class HtmlHelper {
    private static final String BLOCK_TAGS_STRING =
            "article, header, aside, hgroup, blockquote, hr, iframe, body, li, map, button, " +
            "object, canvas, ol, caption, output, col, p, colgroup, pre, dd, progress, div, " +
            "section, dl, table, td, dt, tbody, embed, textarea, fieldset, tfoot, figcaption, " +
            "th, figure, thead, footer, footer, tr, form, ul, h1, h2, h3, h4, h5, h6, video, " +
            "script, style";

    static final Set<String> BLOCK_TAGS = getBlockTagsSet();

    private static Set<String> getBlockTagsSet() {
      Set<String> result = new HashSet<String>();
      String[] tags = BLOCK_TAGS_STRING.split(", ");
      for (String tag : tags) {
        result.add(tag);
      }
      return result;
    }
  }

  private static IElementType getDelimiterTokenType(char c) {
    switch (c) {
      case '"': return MarkdownTokenTypes.DOUBLE_QUOTE;
      case '\'': return MarkdownTokenTypes.SINGLE_QUOTE;
      case '(': return MarkdownTokenTypes.LPAREN;
      case ')': return MarkdownTokenTypes.RPAREN;
      case '[': return MarkdownTokenTypes.LBRACKET;
      case ']': return MarkdownTokenTypes.RBRACKET;
      case '<': return MarkdownTokenTypes.LT;
      case '>': return MarkdownTokenTypes.GT;
      default: return MarkdownTokenTypes.BAD_CHARACTER;
    }
  }

  private IElementType parseDelimited(IElementType contentsType, boolean allowInlines) {
    char first = yycharat(0);
    char last = yycharat(yylength() - 1);

    stateStack.add(yystate());

    parseDelimited.exitChar = last;
    parseDelimited.returnType = contentsType;
    parseDelimited.inlinesAllowed = allowInlines;
//    parseDelimited.inlinesAllowed = true;

    yybegin(PARSE_DELIMITED);

    yypushback(yylength() - 1);
    return getDelimiterTokenType(first);
  }

  private void processEol() {
    int newlinePos = 1;
    while (newlinePos < yylength() && yycharat(newlinePos) != '\n') {
      newlinePos++;
    }

    // there is always one at 0 so that means there are two at least
    if (newlinePos != yylength()) {
      yypushback(yylength() - newlinePos);
      return;
    }

    yybegin(YYINITIAL);
    yypushback(yylength() - 1);

    isHeader = false;
  }

  private void popState() {
    if (stateStack.isEmpty()) {
      yybegin(AFTER_LINE_START);
    }
    else {
      yybegin(stateStack.removeLast());
    }
  }

  private void resetCustom() {
  }

  private void resetState() {
    yypushback(yylength());

    popState();
  }

  private String getTagName() {
    if (yylength() > 1 && yycharat(1) == '/') {
      return yytext().toString().substring(2, yylength() - 1).trim();
    }
    return yytext().toString().substring(1);
  }

  private boolean isBlockTag(String tagName) {
    return HtmlHelper.BLOCK_TAGS.contains(tagName.toLowerCase());
  }

  private boolean canInline() {
    return yystate() == AFTER_LINE_START ||
        yystate() == PARSE_DELIMITED && parseDelimited.inlinesAllowed ||
        yystate() == CODE_SPAN && parseDelimited.inlinesAllowed ||
        yystate() == IN_WIKILINK && parseDelimited.inlinesAllowed;
  }

  private IElementType getReturnGeneralized(IElementType defaultType) {
    if (canInline()) {
      return defaultType;
    }
    return parseDelimited.returnType;
  }

  private int countChars(CharSequence s, char c) {
    int result = 0;
    for (int i = 0; i < s.length(); ++i) {
      if (s.charAt(i) == c)
        result++;
    }
    return result;
  }

  private int calcBalance(int startPos) {
      int balance = 0;
      for (int i = startPos; i >= 0; --i) {
          char c = yycharat(i);
          if (c == ')') {
              balance++;
          }
          else if (c == '(') {
              balance--;
              if (balance <= 0) break;
          }
      }
      return balance;
  }

  private void pushbackAutolink() {
      int length = yylength();
      if (yycharat(length - 1) == '/') {
          while (yycharat(length - 2) == '/') length--;
          yypushback(yylength() - length);
          return;
      }

      int balance = -1;

      // See GFM_AUTOLINK rule
      String badEnding = ".,:;!?\"'*_~]`";

      for (int i = length - 1; i >= 0; --i) {
          char c = yycharat(i);
          if (c == ')') {
              if (balance == -1) {
                  balance = calcBalance(i);
              }

              // If there are not enough opening brackets to match this closing one, drop this bracket
              if (balance > 0) {
                  balance--;
              }
              else {
                  break;
              }
          }
          else if (badEnding.indexOf(c) == -1) {
              break;
          }

          length--;
      }

      yypushback(yylength() - length);
  }

%}

DIGIT = [0-9]
ALPHANUM = [\p{Letter}\p{Number}]
WHITE_SPACE = [ \t\f]
EOL = \R
ANY_CHAR = [^]

DOUBLE_QUOTED_TEXT = \" (\\\" | [^\n\"])* \"
SINGLE_QUOTED_TEXT = "'" (\\"'" | [^\n'])* "'"
QUOTED_TEXT = {SINGLE_QUOTED_TEXT} | {DOUBLE_QUOTED_TEXT}

HTML_COMMENT = "<!" "-"{2,4} ">" | "<!--" ([^-] | "-"[^-])* "-->"
PROCESSING_INSTRUCTION = "<?" ([^?] | "?"[^>])* "?>"
DECLARATION = "<!" [A-Z]+ {WHITE_SPACE}+ [^>] ">"
CDATA = "<![CDATA[" ([^\]] | "]"[^\]] | "]]"[^>])* "]]>"

TAG_NAME = [a-zA-Z]{ALPHANUM}*
ATTRIBUTE_NAME = [a-zA-Z:-] ({ALPHANUM} | [_.:-])*
ATTRIBUTE_VALUE = {QUOTED_TEXT} | [^ \t\f\n\r\"\'=<>`]+
ATTRIBUTE_VALUE_SPEC = {WHITE_SPACE}* "=" {WHITE_SPACE}* {ATTRIBUTE_VALUE}
ATTRUBUTE = {WHITE_SPACE}+ {ATTRIBUTE_NAME} {ATTRIBUTE_VALUE_SPEC}?
OPEN_TAG = "<" {TAG_NAME} {ATTRUBUTE}* {WHITE_SPACE}* "/"? ">"
CLOSING_TAG = "</" {TAG_NAME} {WHITE_SPACE}* ">"
HTML_TAG = {OPEN_TAG} | {CLOSING_TAG} | {HTML_COMMENT} | {PROCESSING_INSTRUCTION} | {DECLARATION} | {CDATA}

TAG_START = "<" {TAG_NAME}
TAG_END = "</" {TAG_NAME} {WHITE_SPACE}* ">"

SCHEME = [a-zA-Z]+
AUTOLINK = "<" {SCHEME} ":" [^ \t\f\n<>]+ ">"
EMAIL_AUTOLINK = "<" [a-zA-Z0-9.!#$%&'*+/=?\^_`{|}~-]+ "@"[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])? (\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)* ">"

HOST_PART={ALPHANUM}([a-zA-Z0-9_-]*{ALPHANUM})?
PATH_PART=[\S&&[^\]()<]]|\][^\[(<]
PATH=({PATH_PART}+ | ("(" {PATH_PART}* ")"? {PATH_PART}*)) ("(" {PATH_PART}* ")"? {PATH_PART}*)*
// See pushbackAutolink method
GFM_AUTOLINK = (("http" "s"? | "ftp" | "file")"://" | "www.") {HOST_PART} ("." {HOST_PART})* (":" [0-9]+)? ("/" {PATH})? "/"?

// ScalaDoc directive
DIRECTIVE_NAME = \S*

charEscapeSeq = \\[^\r\n]
LineTerminator = \r | \n | \r\n | \u0085 |  \u2028 | \u2029 | \u000A | \u000a
charExtra = !( ![^"\""`] | {LineTerminator})             //This is for `type` identifiers
stringElementExtra = {charExtra} | {charEscapeSeq}
stringLiteralExtra = {stringElementExtra}*
SCALA_REF_LINK_TEXT = ("`" {stringLiteralExtra} "`" | ("\\" .) | [^\]\\`\ \t\f\n\r])+

%state TAG_START, AFTER_LINE_START, PARSE_DELIMITED, CODE_SPAN, IN_WIKILINK, AFTER_TAG_WITH_PARAMETER

%%



<YYINITIAL> {

  // blockquote
  {WHITE_SPACE}{0,3} ">" {
    return MarkdownTokenTypes.BLOCK_QUOTE;
  }

  {ANY_CHAR} {
    resetState();
  }

  {WHITE_SPACE}* {EOL} {
    resetState();
  }
}

<IN_WIKILINK> {
  {SCALA_REF_LINK_TEXT} {
    return MarkdownTokenTypes.TEXT;
  }

  {WHITE_SPACE}+ {
    if (wikilinkOpenLength == 0) {
      // link was started after @tag
      popState();
    } else {
      yybegin(AFTER_LINE_START);
    }
    return MarkdownTokenTypes.WHITE_SPACE;
  }

  "]" "]"+ {
    int len = yylength();
    if (wikilinkOpenLength == len) {
      wikilinkOpenLength = 0;
      popState();
      return getDelimiterTokenType(']');
    } else {
      if (len > wikilinkOpenLength) {
        // close it again after
        yypushback(wikilinkOpenLength);
      }
      return MarkdownTokenTypes.TEXT;
    }
  }

  {ANY_CHAR} {
      resetState();
  }
}

<AFTER_LINE_START, PARSE_DELIMITED> {
  // Escaping
  \\[\\\"'`*_{}\[\]()#+.,!:@#$%&~<>/-] {
    return getReturnGeneralized(MarkdownTokenTypes.TEXT);
  }

  // Backticks (code span)
  "`"+ {
    if (canInline()) {
      codeSpanBacktickslength = yylength();
      stateStack.add(yystate());
      yybegin(CODE_SPAN);
      return MarkdownTokenTypes.BACKTICK;
    }
    return parseDelimited.returnType;
  }

  // Math
  "$"+ {
    if (canInline()) {
      return GFMTokenTypes.DOLLAR;
    }
    return parseDelimited.returnType;
  }
}

<CODE_SPAN> {
  "`"+ {
    if (yylength() == codeSpanBacktickslength) {
      codeSpanBacktickslength = 0;
      popState();
    }
    return MarkdownTokenTypes.BACKTICK;
  }
}

<AFTER_LINE_START, PARSE_DELIMITED, CODE_SPAN> {

  // Emphasis
  {WHITE_SPACE}+ ("*" | "_") {WHITE_SPACE}+ {
    return getReturnGeneralized(MarkdownTokenTypes.TEXT);
  }

  "*" | "_" {
    return getReturnGeneralized(MarkdownTokenTypes.EMPH);
  }

  "~" {
    return getReturnGeneralized(GFMTokenTypes.TILDE);
  }

  {AUTOLINK} { return parseDelimited(MarkdownTokenTypes.AUTOLINK, false); }
  {EMAIL_AUTOLINK} { return parseDelimited(MarkdownTokenTypes.EMAIL_AUTOLINK, false); }

  {HTML_TAG} { return MarkdownTokenTypes.HTML_TAG; }

}

<AFTER_LINE_START> {
  "[" "["+ {
    wikilinkOpenLength = yylength();
    stateStack.add(yystate());
    yybegin(IN_WIKILINK);
    return getDelimiterTokenType(yycharat(0));
  }
}


<AFTER_LINE_START, CODE_SPAN> {

  \" | "'"| "(" | ")" | "[" | "]" | "<" | ">" {
    return getDelimiterTokenType(yycharat(0));
  }
  ":" { return MarkdownTokenTypes.COLON; }
  "!" { return MarkdownTokenTypes.EXCLAMATION_MARK; }

  \\ / {EOL} {
    return MarkdownTokenTypes.HARD_LINE_BREAK;
  }
}

<AFTER_LINE_START> {
  "@" ("param" | "tparam" | "throws" | "groupdesc" | "groupname" | "groupprio") {
    yybegin(AFTER_TAG_WITH_PARAMETER);
    return MarkdownTokenTypes.TEXT;
  }

  "@" {DIRECTIVE_NAME} {
    return MarkdownTokenTypes.TEXT;
  }
}

<AFTER_LINE_START, CODE_SPAN> {
  {WHITE_SPACE}+ {
    return MarkdownTokenTypes.WHITE_SPACE;
  }

  {WHITE_SPACE}* ({EOL} {WHITE_SPACE}*)+ {
    int lastSpaces = yytext().toString().indexOf("\n");
    if (lastSpaces >= 2) {
      yypushback(yylength() - lastSpaces);
      return MarkdownTokenTypes.HARD_LINE_BREAK;
    }
    else if (lastSpaces > 0) {
      yypushback(yylength() - lastSpaces);
      return MarkdownTokenTypes.WHITE_SPACE;
    }

    if (yystate() == CODE_SPAN) {
      popState();
    }
    processEol();
    return MarkdownTokenTypes.EOL;
  }

  {GFM_AUTOLINK} {
    pushbackAutolink();
    return GFMTokenTypes.GFM_AUTOLINK;
  }

  {ALPHANUM}+ (({WHITE_SPACE}+ | "_"+) {ALPHANUM}+)* / {WHITE_SPACE}+ {GFM_AUTOLINK} {
    return MarkdownTokenTypes.TEXT;
  }

  // optimize and eat underscores inside words
  {ALPHANUM}+ (({WHITE_SPACE}+ | "_"+) {ALPHANUM}+)* {
    return MarkdownTokenTypes.TEXT;
  }

  {ANY_CHAR} { return MarkdownTokenTypes.TEXT; }

}

<AFTER_TAG_WITH_PARAMETER> {
  {WHITE_SPACE}+ {
    yybegin(IN_WIKILINK);
    return MarkdownTokenTypes.WHITE_SPACE;
  }

  [^] {
    resetState();
  }
}

<PARSE_DELIMITED, CODE_SPAN, IN_WIKILINK> {
  {EOL} { resetState(); }

  {EOL} | {ANY_CHAR} {
    if (yycharat(0) == parseDelimited.exitChar) {
      if (yystate() == CODE_SPAN) {
        stateStack.removeLast();
      }
      yybegin(stateStack.removeLast());
      return getDelimiterTokenType(yycharat(0));
    }
    return parseDelimited.returnType;
  }

}

{ANY_CHAR} { return MarkdownTokenTypes.TEXT; }
