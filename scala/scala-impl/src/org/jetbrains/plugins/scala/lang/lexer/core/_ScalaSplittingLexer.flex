package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes;
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

  public void resetCustom() {
    braceCount = 0;
    commentType = tBLOCK_COMMENT;
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

END_OF_LINE_COMMENT="/""/"[^\r\n]*
SCALA_DIRECTIVE_LINE_COMMENT="//>"\s*"using"[^\r\n]*


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate IN_BLOCK_COMMENT
%%

<YYINITIAL>{
{CHARACTER_LITERAL}        {  return SCALA_PLAIN_CONTENT; }
{STRING_LITERAL}           {  return SCALA_PLAIN_CONTENT; }
{BACKQUOTED_IDENTIFIER}    {  return SCALA_PLAIN_CONTENT; }

{SCALA_DIRECTIVE_LINE_COMMENT} {  return ScalaDirectiveElementTypes.SCALA_DIRECTIVE; }
{END_OF_LINE_COMMENT}              {  return tLINE_COMMENT; }

{SIMPLE_BLOCK_COMMENT}   {  return tBLOCK_COMMENT; }

{DOC_COMMENT_BEGIN}      {  commentType = ScalaDocElementTypes.SCALA_DOC_COMMENT;
                            braceCount++;
                            yybegin(IN_BLOCK_COMMENT);
                            return(commentType);
                         }

{COMMENT_BEGIN}          {  commentType = tBLOCK_COMMENT;
                            braceCount++;
                            yybegin(IN_BLOCK_COMMENT);
                            return(commentType);
                         }

[^]                      {  return SCALA_PLAIN_CONTENT; }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////  Block comment processing ////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
<IN_BLOCK_COMMENT> {

{COMMENT_BEGIN}          {  braceCount++;
                            return(commentType);
                         }

{COMMENT_END}            {  braceCount--;
                            if (braceCount == 0) {
                              yybegin(YYINITIAL);
                            }
                            return(commentType);
                         }

[^]                      {  return(commentType); }

}
