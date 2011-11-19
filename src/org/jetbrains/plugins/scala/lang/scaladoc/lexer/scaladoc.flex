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
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.*;

%%

%class _ScalaDocLexer
%implements FlexLexer, ScalaDocTokenType
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
////////// ScalaDoc lexems ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state COMMENT_DATA_START
%state COMMENT_DATA
%state TAG_DOC_SPACE
%state PARAM_TAG_DOC_SPACE
%state PARAM_TAG_SPACE
%state DOC_TAG_VALUE
%state PARAM_DOC_TAG_VALUE
%state DOC_TAG_VALUE_IN_PAREN
%state DOC_TAG_VALUE_IN_LTGT
%state INLINE_TAG_NAME
%state INLINE_DOC_TAG_VALUE
%state INLINE_TAG_DOC_SPACE
%state CODE_LINK_INNER
%state CODE_BAD_LINK
%state HTTP_LINK_INNER
%state COMMENT_INNER_CODE
%state INNER_CODE_WHITESPACE

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_NL=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:]
IDENTIFIER={ALPHA}({ALPHA}|{DIGIT}|[":.-"])*


/////////////////////////////////// for arbitrary scala identifiers////////////////////////////////////////////////////
special = \u0021 | \u0023 | [\u0025-\u0026] | [\u002A-\u002B] | \u002D | \u005E | \u003A| [\u003C-\u0040]| \u007E
          | \u005C | \u002F | [:unicode_math_symbol:] | [:unicode_other_symbol:]
LineTerminator = \r | \n | \r\n | \u0085 |  \u2028 | \u2029 | \u000A | \u000a

op = \u007C ({special} | \u007C)+ | {special} ({special} | \u007C)*
octalDigit = [0-7]
idrest1 = [:jletter:]? [:jletterdigit:]* ("_" {op})?
idrest = [:jletter:]? [:jletterdigit:]* ("_" {op} | "_" {idrest1} )?
varid = [:jletter:] {idrest}
charEscapeSeq = \\[^\r\n]
charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
stringElement = {charNoDoubleQuote} | {charEscapeSeq}
stringLiteral = {stringElement}*
charExtra = !( ![^"\""`] | {LineTerminator})             //This is for `type` identifiers
stringElementExtra = {charExtra} | {charEscapeSeq}
stringLiteralExtra = {stringElementExtra}*
symbolLiteral = "\'" {plainid}
plainid = {varid} | {op}
scalaIdentifierWithPath = (({plainid} | "`" {stringLiteralExtra} "`")["."]?)+
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%%

<YYINITIAL> "/**" { yybegin(COMMENT_DATA_START); return DOC_COMMENT_START; }
<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { return DOC_WHITESPACE; }
<COMMENT_DATA>  {WHITE_DOC_SPACE_NO_NL}+ { return DOC_COMMENT_DATA; }
<COMMENT_DATA, COMMENT_INNER_CODE>  [\n\r]+{WHITE_DOC_SPACE_CHAR}* { return DOC_WHITESPACE; }


<COMMENT_DATA, COMMENT_DATA_START> ("__"|"\u005f\u005f") {
  return DOC_UNDERLINE_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> ("'''"|"\u0027\u0027\u0027") {
  return DOC_BOLD_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> ("''"|"\u0027\u0027") {
  return DOC_ITALIC_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> ("^"|"\u005e") {
  return DOC_SUPERSCRIPT_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> (",,"|"\u002c\u002c") {
  return DOC_SUBSCRIPT_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> ("`"|"\u0060") {
  return DOC_MONOSPACE_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START, TAG_DOC_SPACE> ("[["|"\u005b\u005b") {
  yybegin(CODE_LINK_INNER);
  return DOC_LINK_TAG;
}
<COMMENT_DATA, COMMENT_DATA_START> ("{{{"|"\u007b\u007b\u007b") {
  yybegin(COMMENT_INNER_CODE);
  return DOC_INNER_CODE_TAG;
}
<COMMENT_DATA> ("="|"\u003d")+ {
  return DOC_HEADER;
}
<COMMENT_DATA_START> ("="|"\u003d")+ {
  return VALID_DOC_HEADER;
}


/*<COMMENT_INNER_CODE> [\t\f\n\r]+(\ )* {
  //yybegin(INNER_CODE_WHITESPACE);
  return DOC_WHITESPACE;
} */
/*
<INNER_CODE_WHITESPACE> \*+ {
 yybegin(COMMENT_INNER_CODE);
 return DOC_COMMENT_DATA;
}*/
<COMMENT_INNER_CODE> . {
  yybegin(COMMENT_INNER_CODE);
  return DOC_INNER_CODE;
}
<COMMENT_INNER_CODE> ("}}}"|"\u007d\u007d\u007d") {
  yybegin(COMMENT_DATA);
  return DOC_INNER_CODE_TAG;
}

<COMMENT_DATA, COMMENT_DATA_START, TAG_DOC_SPACE> ("[["|"\u005b\u005b") / "http:" {
  yybegin(COMMENT_DATA);
  return DOC_HTTP_LINK_TAG;
}
<CODE_LINK_INNER> {scalaIdentifierWithPath} / " " {
  yybegin(CODE_BAD_LINK);
  return DOC_COMMENT_DATA;
}
<CODE_LINK_INNER> {scalaIdentifierWithPath} / ("]]"|"\u005d\u005d") {
  return DOC_COMMENT_DATA;
}
<CODE_BAD_LINK> . {
  return DOC_COMMENT_BAD_CHARACTER;
}
/*<HTTP_LINK_INNER> . {
  return DOC_COMMENT_DATA;
}*/
<CODE_LINK_INNER, COMMENT_DATA, CODE_BAD_LINK, COMMENT_DATA_START> ("]]"|"\u005d\u005d") {
  yybegin(COMMENT_DATA);
  return DOC_LINK_CLOSE_TAG;
}
/*<HTTP_LINK_INNER> "]]" {
  yybegin(COMMENT_DATA);
  return DOC_LINK_CLOSE_TAG;
}     */

<DOC_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return DOC_WHITESPACE; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> ({ALPHA}|[_0-9\."$"\[\]])+ { return DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE> [\(] { yybegin(DOC_TAG_VALUE_IN_PAREN); return DOC_TAG_VALUE_LPAREN; }
<DOC_TAG_VALUE_IN_PAREN> [\)] { yybegin(DOC_TAG_VALUE); return DOC_TAG_VALUE_RPAREN; }
<DOC_TAG_VALUE> [#] { return DOC_TAG_VALUE_SHARP_TOKEN; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> [,] { return DOC_TAG_VALUE_COMMA; }
<DOC_TAG_VALUE_IN_PAREN> {WHITE_DOC_SPACE_CHAR}+ { return DOC_WHITESPACE; }

// <INLINE_TAG_NAME, COMMENT_DATA_START> "@param" { yybegin(PARAM_TAG_SPACE); return DOC_TAG_NAME; }
// <PARAM_TAG_SPACE>  {WHITE_DOC_SPACE_CHAR}+ {yybegin(DOC_TAG_VALUE); return DOC_WHITESPACE;}
// <DOC_TAG_VALUE> [\<] { yybegin(DOC_TAG_VALUE_IN_LTGT); return DOC_TAG_VALUE_LT; }
// <DOC_TAG_VALUE_IN_LTGT> {IDENTIFIER} { return DOC_TAG_VALUE_TOKEN; }
// <DOC_TAG_VALUE_IN_LTGT> [\>] { yybegin(COMMENT_DATA); return DOC_TAG_VALUE_GT; }

<COMMENT_DATA_START, COMMENT_DATA> "{" / "@"{IDENTIFIER} {
  /*if (checkAhead('@')){*/
    yybegin(INLINE_TAG_NAME);
/*  }
  else{
    yybegin(COMMENT_DATA);
  }*/
  return DOC_INLINE_TAG_START;
}
<INLINE_TAG_NAME> "@"{IDENTIFIER} { yybegin(INLINE_TAG_DOC_SPACE); return DOC_TAG_NAME; }
<INLINE_TAG_DOC_SPACE, INLINE_DOC_TAG_VALUE> "}" { yybegin(COMMENT_DATA); return DOC_INLINE_TAG_END; }
<INLINE_DOC_TAG_VALUE> [^\}]+ { return DOC_TAG_VALUE_TOKEN; }

<COMMENT_DATA_START, COMMENT_DATA, DOC_TAG_VALUE> . {
  yybegin(COMMENT_DATA);
  return DOC_COMMENT_DATA;
}


/*
<DOC_TAG_VALUE> [^\n] {
  yybegin(COMMENT_DATA);
  return markStorage.isMarkedElement() ? markStorage.getMarkedElement() : DOC_COMMENT_DATA;
}
 */

<COMMENT_DATA_START> "@"("param"|"tparam"|"throws") {yybegin(PARAM_TAG_DOC_SPACE); return DOC_TAG_NAME; }
<PARAM_TAG_DOC_SPACE> {WHITE_DOC_SPACE_NO_NL}+ {yybegin(PARAM_DOC_TAG_VALUE); return DOC_WHITESPACE;}
<PARAM_DOC_TAG_VALUE> ({plainid} | "`" {stringLiteralExtra} "`") {yybegin(COMMENT_DATA); return DOC_TAG_VALUE_TOKEN; }
<PARAM_DOC_TAG_VALUE> [\r\n]+ {yybegin(COMMENT_DATA); return DOC_WHITESPACE;}

<COMMENT_DATA_START> "@"{IDENTIFIER} {yybegin(TAG_DOC_SPACE); return DOC_TAG_NAME;  }
<TAG_DOC_SPACE>  {WHITE_DOC_SPACE_CHAR}+ {
   yybegin(COMMENT_DATA);
   return DOC_WHITESPACE;
}
/*
<TAG_DOC_SPACE>  {WHITE_DOC_SPACE_CHAR}+ {
  if (checkAhead('<') || checkAhead('\"') || checkAhead('\u007b')) yybegin(COMMENT_DATA);
  //else if (checkAhead('\u007b') ) yybegin(COMMENT_DATA); //lbrace -  there's some error in JLex when typing lbrace directly
  else yybegin(DOC_TAG_VALUE);

 return DOC_WHITESPACE;
} */
<INLINE_TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ {
  yybegin(INLINE_DOC_TAG_VALUE);
  return DOC_WHITESPACE;
}

\*+"/" { return DOC_COMMENT_END; }
[^] { return DOC_COMMENT_BAD_CHARACTER; }