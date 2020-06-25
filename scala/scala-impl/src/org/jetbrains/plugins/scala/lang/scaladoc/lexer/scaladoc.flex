/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

%%

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// ATTENTION!
// Some extra lexer magic is done inside
// org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocAsteriskStripperLexer
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%class _ScalaDocLexer
%implements FlexLexer, ScalaDocTokenType, ScalaTokenTypes
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// User code //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{ // User code

  private boolean isOddItalicBold = false;;
  private int braceCount = 0;

  public _ScalaDocLexer() {
    this((java.io.Reader)null);
  }

  public boolean checkAhead(char c) {
     if (zzMarkedPos >= zzBuffer.length()) return false;
     return zzBuffer.charAt(zzMarkedPos) == c;
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzPushbackPos = 0;
    zzAtEOF = offset < zzEndRead;
  }

%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// ScalaDoc lexems /////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state COMMENT_DATA_START
%state COMMENT_DATA
%state LIST_ITEM_HEAD
%state LIST_ITEM_DATA_START
%state TAG_DOC_SPACE
%state PARAM_TAG_DOC_SPACE
%state PARAM_THROWS_TAG_DOC_SPACE
%state PARAM_DEFINE_TAG_DOC_SPACE
%state PARAM_TAG_SPACE
%state DOC_TAG_VALUE
%state PARAM_DOC_TAG_VALUE
%state PARAM_DOC_THROWS_TAG_VALUE
%state PARAM_DOC_DEFINE_TAG_VALUE
%state DOC_TAG_VALUE_IN_PAREN
%state DOC_TAG_VALUE_IN_LTGT
%state INLINE_TAG_NAME
%state INLINE_LINK_TAG_DOC_SPACE
%state INLINE_LINK_TAG_VALUE
%state INLINE_DOC_TAG_VALUE
%state INLINE_TAG_DOC_SPACE
%state CODE_LINK_INNER_START
%state CODE_LINK_INNER
%state HTTP_LINK_INNER_START
%state HTTP_LINK_INNER
%state CODE_BAD_LINK
%state DOC_TAG_VALUE_SPACE
%xstate COMMENT_INNER_CODE
%xstate INNER_CODE_WHITESPACE

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_NL=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:]

TAG_IDENTIFIER=[^\ \t\f\n\r}]+ // SCL-13537
MACRO_IDENTIFIER=("{" .* "}") | ({ALPHA} | {DIGIT})+ // SCL-9720

COMMENT_BEGIN = "/*"
DOC_COMMENT_BEGIN = "/*""*"
COMMENT_END = "*/"

LIST_ITEM_HEAD_REG= (\-|1\.|I\.|i\.|A\.|a\.)

/////////////////////////////////// for arbitrary scala identifiers////////////////////////////////////////////////////
special = \u0021 | \u0023 | [\u0025-\u0026] | [\u002A-\u002B] | \u002D | \u005E | \u003A| [\u003C-\u0040]| \u007E
          | \u005C | \u002F | [:unicode_math_symbol:] | [:unicode_other_symbol:]
LineTerminator = \r | \n | \r\n | \u0085 |  \u2028 | \u2029 | \u000A | \u000a

op = \u007C ({special} | \u007C)+ | {special} ({special} | \u007C)*
//octalDigit = [0-7]
idrest1 = [:jletter:]? [:jletterdigit:]* ("_" {op})?
idrest = [:jletter:]? [:jletterdigit:]* ("_" {op} | "_" {idrest1} )?
varid = [:jletter:] {idrest}
charEscapeSeq = \\[^\r\n]
//charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
//stringElement = {charNoDoubleQuote} | {charEscapeSeq}
//stringLiteral = {stringElement}*
charExtra = !( ![^"\""`] | {LineTerminator})             //This is for `type` identifiers
stringElementExtra = {charExtra} | {charEscapeSeq}
stringLiteralExtra = {stringElementExtra}*
//symbolLiteral = "\'" {plainid}
plainid = {varid} | {op}
//scalaIdentifierWithPath = (({plainid} | "`" {stringLiteralExtra} "`")["."]?)+
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%%

<YYINITIAL> {DOC_COMMENT_BEGIN} {
  braceCount++;
  yybegin(COMMENT_DATA_START);
  return DOC_COMMENT_START;
}
{DOC_COMMENT_BEGIN} | {COMMENT_BEGIN} {
  braceCount++;
  yybegin(COMMENT_DATA);
  return DOC_COMMENT_DATA;
}
{COMMENT_END} {
  braceCount--;
  if (braceCount == 0) {
    return DOC_COMMENT_END;
  }
  else {
    yybegin(COMMENT_DATA);
    return DOC_COMMENT_DATA;
  }
}

////////////////////////////////////////////////////////////////////////////////////////////
// List item head, e.g. '  1. list item'
////////////////////////////////////////////////////////////////////////////////////////////
// looks like jlex doesn't support {2,} syntax (2 or more), using fixed big magic constant
<COMMENT_DATA_START> {WHITE_DOC_SPACE_NO_NL}{2,999} / {LIST_ITEM_HEAD_REG}{WHITE_DOC_SPACE_CHAR} {
  yybegin(LIST_ITEM_HEAD); return DOC_WHITESPACE;
}
<LIST_ITEM_HEAD> {LIST_ITEM_HEAD_REG} / {WHITE_DOC_SPACE_CHAR} { yybegin(LIST_ITEM_DATA_START); return DOC_LIST_ITEM_HEAD; }
<LIST_ITEM_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return DOC_WHITESPACE; }


<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { return DOC_WHITESPACE; }
<COMMENT_DATA>  {WHITE_DOC_SPACE_NO_NL}+ { return DOC_COMMENT_DATA; }
<COMMENT_DATA, COMMENT_INNER_CODE>  [\n\r]+{WHITE_DOC_SPACE_CHAR}* { return DOC_WHITESPACE; }


////////////////////////////////////////////////////////////////////////////////////////////
// Markup syntax e.g. __underlined text__, '''old text'''
////////////////////////////////////////////////////////////////////////////////////////////
<COMMENT_DATA, COMMENT_DATA_START> ("__"|"\u005f\u005f") { return DOC_UNDERLINE_TAG; }
<COMMENT_DATA, COMMENT_DATA_START> ("^"|"\u005e") { return DOC_SUPERSCRIPT_TAG; }
<COMMENT_DATA, COMMENT_DATA_START> (",,"|"\u002c\u002c") { return DOC_SUBSCRIPT_TAG; }
<COMMENT_DATA, COMMENT_DATA_START> ("`"|"\u0060") { return DOC_MONOSPACE_TAG; }
<COMMENT_DATA> ("="|"\u003d")+ { return DOC_HEADER; }
<COMMENT_DATA_START> ("="|"\u003d")+ { return VALID_DOC_HEADER; }
<COMMENT_DATA, COMMENT_DATA_START> ("''"|"\u0027\u0027") { return DOC_ITALIC_TAG; }
<COMMENT_DATA, COMMENT_DATA_START> "'''" / ("''"[^"'"]) {
  if (isOddItalicBold) {
    isOddItalicBold = false;
    yypushback(1);
    return DOC_ITALIC_TAG;
  }
  isOddItalicBold = true;
  return DOC_BOLD_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> ("'''"|"\u0027\u0027\u0027") { return DOC_BOLD_TAG; }

<COMMENT_DATA, COMMENT_DATA_START> "$"{MACRO_IDENTIFIER} { return DOC_MACROS; }


<COMMENT_DATA, COMMENT_DATA_START> ("{{{"|"\u007b\u007b\u007b") {
  yybegin(COMMENT_INNER_CODE);
  return DOC_INNER_CODE_TAG;
}
<COMMENT_INNER_CODE> . {
  yybegin(COMMENT_INNER_CODE);
  return DOC_INNER_CODE;
}
<COMMENT_INNER_CODE> ("}}}"|"\u007d\u007d\u007d") {
  yybegin(COMMENT_DATA);
  return DOC_INNER_CLOSE_CODE_TAG;
}


////////////////////////////////////////////////////////////////////////////////////////////
// Code links e.g. [[scala.collection.Seq]], [[scala.collection.Seq label text]]
// HTTP links e.g. [[http://example.org]], [[https://example.org label text]]
////////////////////////////////////////////////////////////////////////////////////////////
<COMMENT_DATA, COMMENT_DATA_START, TAG_DOC_SPACE> ("[["|"\u005b\u005b") {
  yybegin(CODE_LINK_INNER_START);
  return DOC_LINK_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START, TAG_DOC_SPACE> ("[["|"\u005b\u005b") / {WHITE_DOC_SPACE_NO_NL}*("http:" | "https:") {
  yybegin(HTTP_LINK_INNER_START);
  return DOC_HTTP_LINK_TAG;
}

<HTTP_LINK_INNER_START> {WHITE_DOC_SPACE_NO_NL}+ {
  yybegin(HTTP_LINK_INNER);
  return DOC_WHITESPACE;
}

<HTTP_LINK_INNER> {WHITE_DOC_SPACE_NO_NL}+ {
  yybegin(COMMENT_DATA);
  return DOC_WHITESPACE;
}
<HTTP_LINK_INNER_START, HTTP_LINK_INNER> [^\ \t\f\n\r\]\u005d]+ {
  yybegin(HTTP_LINK_INNER);
  return DOC_HTTP_LINK_VALUE;
}

<CODE_LINK_INNER, CODE_LINK_INNER_START,HTTP_LINK_INNER, HTTP_LINK_INNER_START, CODE_BAD_LINK,COMMENT_DATA, COMMENT_DATA_START> ("]]"|"\u005d\u005d") {
  yybegin(COMMENT_DATA);
  return DOC_LINK_CLOSE_TAG;
}

<CODE_LINK_INNER_START> {WHITE_DOC_SPACE_NO_NL}+ {
  yybegin(CODE_LINK_INNER);
  return DOC_WHITESPACE;
}
<CODE_LINK_INNER_START, CODE_LINK_INNER> ({plainid} | "`" {stringLiteralExtra} "`") {
  yybegin(CODE_LINK_INNER);
  return tIDENTIFIER;
}
<CODE_LINK_INNER> "." {
  return tDOT;
}
<CODE_LINK_INNER> {WHITE_DOC_SPACE_NO_NL}+ {
  yybegin(COMMENT_DATA);
  return DOC_WHITESPACE;
}
<CODE_BAD_LINK> [\n\r]+({WHITE_DOC_SPACE_CHAR})* {
  yybegin(COMMENT_DATA);
  return DOC_WHITESPACE;
}
<CODE_BAD_LINK> . {
  return DOC_COMMENT_BAD_CHARACTER;
}


////////////////////////////////////////////////////////////////////////////////////////////
// JavaDoc - style inlie tags e.g. {@code 2 + 2 == 4}
////////////////////////////////////////////////////////////////////////////////////////////
<COMMENT_DATA_START, COMMENT_DATA> "{" / "@"{TAG_IDENTIFIER} {
  yybegin(INLINE_TAG_NAME);
  return DOC_INLINE_TAG_START;
}
<INLINE_TAG_NAME> "@"("link" | "linkplain") { yybegin(INLINE_LINK_TAG_DOC_SPACE); return DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@"{TAG_IDENTIFIER} {  yybegin(INLINE_TAG_DOC_SPACE); return DOC_TAG_NAME;  }
<INLINE_LINK_TAG_DOC_SPACE> {WHITE_DOC_SPACE_NO_NL}+ { yybegin(INLINE_LINK_TAG_VALUE); return DOC_WHITESPACE;  }
<INLINE_LINK_TAG_VALUE> ({plainid} | "`" {stringLiteralExtra} "`") { return tIDENTIFIER; }
<INLINE_LINK_TAG_VALUE> "." { return tDOT; }
<INLINE_LINK_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(INLINE_DOC_TAG_VALUE); return DOC_WHITESPACE;  }
<INLINE_TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(INLINE_DOC_TAG_VALUE);return DOC_WHITESPACE; }
<INLINE_DOC_TAG_VALUE> [^\}]+ { return DOC_COMMENT_DATA; }

<INLINE_TAG_DOC_SPACE, INLINE_LINK_TAG_DOC_SPACE, INLINE_DOC_TAG_VALUE, INLINE_LINK_TAG_VALUE> "}" {
  yybegin(COMMENT_DATA);
  return DOC_INLINE_TAG_END;
}

<COMMENT_DATA_START, COMMENT_DATA, DOC_TAG_VALUE> . {
  yybegin(COMMENT_DATA);
  return DOC_COMMENT_DATA;
}


////////////////////////////////////////////////////////////////////////////////////////////
// tags e.g.
// @param pName some description
// @returns some value
// etc...
////////////////////////////////////////////////////////////////////////////////////////////
<DOC_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return DOC_WHITESPACE; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> ({ALPHA}|[_0-9\."$"\[\]])+ { return DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE> [\(] { yybegin(DOC_TAG_VALUE_IN_PAREN); return DOC_TAG_VALUE_LPAREN; }
<DOC_TAG_VALUE_IN_PAREN> [\)] { yybegin(DOC_TAG_VALUE); return DOC_TAG_VALUE_RPAREN; }
<DOC_TAG_VALUE> [#] { return DOC_TAG_VALUE_SHARP_TOKEN; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> [,] { return DOC_TAG_VALUE_COMMA; }
<DOC_TAG_VALUE_IN_PAREN> {WHITE_DOC_SPACE_CHAR}+ { return DOC_WHITESPACE; }

<COMMENT_DATA_START> "@define" {yybegin(PARAM_DEFINE_TAG_DOC_SPACE); return DOC_TAG_NAME; }
<PARAM_DEFINE_TAG_DOC_SPACE> {WHITE_DOC_SPACE_NO_NL}+ {yybegin(PARAM_DOC_DEFINE_TAG_VALUE); return DOC_COMMENT_DATA;}
<PARAM_DOC_DEFINE_TAG_VALUE> {MACRO_IDENTIFIER} { yybegin(DOC_TAG_VALUE_SPACE); return DOC_TAG_VALUE_TOKEN; }

<PARAM_DEFINE_TAG_DOC_SPACE, PARAM_DOC_DEFINE_TAG_VALUE> [^] {
  yypushback(1);
  yybegin(COMMENT_DATA);
}

<COMMENT_DATA_START> "@throws" {yybegin(PARAM_THROWS_TAG_DOC_SPACE); return DOC_TAG_NAME; }
<PARAM_THROWS_TAG_DOC_SPACE> {WHITE_DOC_SPACE_NO_NL}+ {yybegin(PARAM_DOC_THROWS_TAG_VALUE); return DOC_COMMENT_DATA;}
<PARAM_THROWS_TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ {yybegin(COMMENT_DATA); return DOC_WHITESPACE;}
<PARAM_DOC_THROWS_TAG_VALUE> ({plainid} | "`" {stringLiteralExtra} "`") {
  yybegin(DOC_TAG_VALUE_SPACE);
  return tIDENTIFIER;
}
<PARAM_DOC_THROWS_TAG_VALUE> ({plainid} | "`" {stringLiteralExtra} "`") / ["."] { return tIDENTIFIER; }
<PARAM_DOC_THROWS_TAG_VALUE> "." { return tDOT; }

<COMMENT_DATA_START> "@"("param"|"tparam"|"define") {yybegin(PARAM_TAG_DOC_SPACE); return DOC_TAG_NAME; }
<PARAM_TAG_DOC_SPACE> {WHITE_DOC_SPACE_NO_NL}+ {yybegin(PARAM_DOC_TAG_VALUE); return DOC_COMMENT_DATA;}
<PARAM_TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ {yybegin(COMMENT_DATA); return DOC_WHITESPACE;}
<PARAM_DOC_TAG_VALUE> ({plainid} | "`" {stringLiteralExtra} "`") {yybegin(DOC_TAG_VALUE_SPACE); return DOC_TAG_VALUE_TOKEN; }

<DOC_TAG_VALUE_SPACE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return DOC_WHITESPACE; }
<DOC_TAG_VALUE_SPACE> . { yybegin(COMMENT_DATA); return DOC_COMMENT_DATA; }

<COMMENT_DATA_START> "@"{TAG_IDENTIFIER} {yybegin(TAG_DOC_SPACE); return DOC_TAG_NAME;  }
<TAG_DOC_SPACE>  {WHITE_DOC_SPACE_CHAR}+ {
   yybegin(COMMENT_DATA);
   return DOC_WHITESPACE;
}

[^] { return DOC_COMMENT_BAD_CHARACTER; }