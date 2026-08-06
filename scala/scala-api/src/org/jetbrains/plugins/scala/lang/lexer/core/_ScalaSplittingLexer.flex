package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes;

import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings({"ALL"})
%%

%class _ScalaSplittingLexer
%implements FlexLexer, ScalaTokenTypesEx, ScalaDocTokenType
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{

  private int braceCount = 0;
  private IElementType commentType = tBLOCK_COMMENT;
  private final Deque<InterpolatedString> interpolatedStrings = new ArrayDeque<>();

  private static final class InterpolatedString {
    final int state;
    final boolean raw;
    int braces;

    InterpolatedString(int state, boolean raw) {
      this.state = state;
      this.raw = raw;
    }
  }

  private void startInterpolatedString(int state, int quoteCount) {
    boolean raw = "raw".contentEquals(yytext().subSequence(0, yylength() - quoteCount));
    interpolatedStrings.push(new InterpolatedString(state, raw));
    yybegin(state);
  }

  private void endInterpolatedString() {
    interpolatedStrings.pop();
    yybegin(codeState());
  }

  private int codeState() {
    return interpolatedStrings.isEmpty() ? YYINITIAL : IN_INTERPOLATION;
  }

  private IElementType commentToken(IElementType type) {
    // Splitting an injection would reset the core lexer's interpolation stack.
    return interpolatedStrings.isEmpty() ? type : SCALA_PLAIN_CONTENT;
  }

  public void resetCustom() {
    braceCount = 0;
    commentType = tBLOCK_COMMENT;
    interpolatedStrings.clear();
  }
%}

COMMENT_BEGIN = "/*"
SIMPLE_BLOCK_COMMENT = "/**/"
DOC_COMMENT_BEGIN = "/*""*"
COMMENT_END = "*/"

octalDigit = [0-7]
hexDigit = [0-9A-Fa-f]
ESCAPE_SEQUENCE=\\[^\r\n]
UNICODE_ESCAPE=!(!(\\u{hexDigit}{hexDigit}{hexDigit}{hexDigit}) | \\u000A)
SOME_ESCAPE=\\{octalDigit} {octalDigit}? {octalDigit}?
CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE}|{UNICODE_ESCAPE}|{SOME_ESCAPE})("'"|\\) | \'\\u000A\'
STRING_LITERAL = \"([^\\\"\r\n] | {ESCAPE_SEQUENCE})*(\"|\\)? | {MULTI_LINE_STRING}
MULTI_LINE_STRING = \"\"\" ( (\"(\")?)? [^\"] )* \"\"\" (\")* // Multi-line string

BACKQUOTED_IDENTIFIER=\`[^`]*\`

// Keep interpolator identifiers in sync with varid in _ScalaCoreLexer.flex.
OPCHAR = "!" | "#" | "%" | "&" | "*" | "+" | "-" | "/" | ":" | "<" | "=" | ">"
       | "?" | "@" | "\\" | "^" | "|" | "~" | \p{So} | \p{Sm}
INTERPOLATOR = [:jletter:] [:jletterdigit:]* ("_" {OPCHAR}+)?

END_OF_LINE_COMMENT="/""/"[^\r\n]*
SCALA_DIRECTIVE_LINE_COMMENT="//>"\s*"using"[^\r\n]*


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate IN_BLOCK_COMMENT
%xstate IN_INTERPOLATION
%xstate IN_INTERPOLATED_STRING
%xstate IN_MULTILINE_INTERPOLATED_STRING
%%

<YYINITIAL, IN_INTERPOLATION>{
{INTERPOLATOR}\"\"\"   {  startInterpolatedString(IN_MULTILINE_INTERPOLATED_STRING, 3);
                            return SCALA_PLAIN_CONTENT;
                         }
{INTERPOLATOR}\"         {  startInterpolatedString(IN_INTERPOLATED_STRING, 1);
                            return SCALA_PLAIN_CONTENT;
                         }
{CHARACTER_LITERAL}        {  return SCALA_PLAIN_CONTENT; }
{STRING_LITERAL}           {  return SCALA_PLAIN_CONTENT; }
{BACKQUOTED_IDENTIFIER}    {  return SCALA_PLAIN_CONTENT; }

{SCALA_DIRECTIVE_LINE_COMMENT} {  return commentToken(ScalaDirectiveElementTypes.SCALA_DIRECTIVE); }
{END_OF_LINE_COMMENT}              {  return commentToken(tLINE_COMMENT); }

{SIMPLE_BLOCK_COMMENT}   {  return commentToken(tBLOCK_COMMENT); }

{DOC_COMMENT_BEGIN}      {  commentType = ScalaDocElementTypes.SCALA_DOC_COMMENT;
                            braceCount++;
                            yybegin(IN_BLOCK_COMMENT);
                            return commentToken(commentType);
                         }

{COMMENT_BEGIN}          {  commentType = tBLOCK_COMMENT;
                            braceCount++;
                            yybegin(IN_BLOCK_COMMENT);
                            return commentToken(commentType);
                         }

}

<IN_INTERPOLATION> {
"{"                      {  interpolatedStrings.peek().braces++;
                            return SCALA_PLAIN_CONTENT;
                         }
"}"                      {  InterpolatedString string = interpolatedStrings.peek();
                            if (--string.braces == 0) yybegin(string.state);
                            return SCALA_PLAIN_CONTENT;
                         }
}

<IN_INTERPOLATED_STRING, IN_MULTILINE_INTERPOLATED_STRING> {
"$$"                     {  return SCALA_PLAIN_CONTENT; }
"$"{BACKQUOTED_IDENTIFIER} { return SCALA_PLAIN_CONTENT; }
"${"                     {  interpolatedStrings.peek().braces = 1;
                            yybegin(IN_INTERPOLATION);
                            return SCALA_PLAIN_CONTENT;
                         }
}

<IN_INTERPOLATED_STRING> {
[^\\\"\r\n$]+             {  return SCALA_PLAIN_CONTENT; }
{ESCAPE_SEQUENCE}         {  if (interpolatedStrings.peek().raw) yypushback(1);
                            return SCALA_PLAIN_CONTENT;
                         }
\"                       {  endInterpolatedString();
                            return SCALA_PLAIN_CONTENT;
                         }
[\r\n]                   {  endInterpolatedString();
                            return SCALA_PLAIN_CONTENT;
                         }
}

<IN_MULTILINE_INTERPOLATED_STRING> {
[^\"$]+                  {  return SCALA_PLAIN_CONTENT; }
\"\"\" (\")*            {  endInterpolatedString();
                            return SCALA_PLAIN_CONTENT;
                         }
}

<YYINITIAL, IN_INTERPOLATION, IN_INTERPOLATED_STRING, IN_MULTILINE_INTERPOLATED_STRING>
[^]                      {  return SCALA_PLAIN_CONTENT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////  Block comment processing ////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_BLOCK_COMMENT> {

{COMMENT_BEGIN}          {  braceCount++;
                            return commentToken(commentType);
                         }

{COMMENT_END}            {  braceCount--;
                            if (braceCount == 0) {
                              yybegin(codeState());
                            }
                            return commentToken(commentType);
                         }

[^]                      {  return commentToken(commentType); }

}
