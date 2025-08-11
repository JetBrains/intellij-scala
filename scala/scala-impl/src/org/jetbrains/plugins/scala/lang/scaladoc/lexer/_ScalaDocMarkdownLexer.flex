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

@SuppressWarnings({"ALL"})
%%

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// ATTENTION!
// Some extra lexer magic is done inside
// org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocAsteriskStripperLexer
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%class _ScalaDocMarkdownLexer
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

  public _ScalaDocMarkdownLexer() {
    this(null);
  }

  private int braceCount = 0; // tracks deepness of nested doc comments (/** and */)

  public boolean checkAhead(char c) {
    if (zzMarkedPos >= zzBuffer.length()) return false;
    return zzBuffer.charAt(zzMarkedPos) == c;
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzAtEOF = false;
  }

  public void resetCustom() {
    braceCount = 0;
  }
%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// ScalaDoc lexems /////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state COMMENT_DATA_START
%state COMMENT_DATA

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_NL=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:] // see java.lang.Character.isJavaIdentifierPart
ALPHA_RAW=[a-zA-Z] // e.g. does not include $ or _

COMMENT_BEGIN = "/*"
DOC_COMMENT_BEGIN = "/*""*"
COMMENT_END = "*/"

LEADING_ASTERISK = "*"

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

<COMMENT_DATA_START> {LEADING_ASTERISK} {
  yybegin(COMMENT_DATA);
  return DOC_COMMENT_LEADING_ASTERISKS;
}

<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { return DOC_WHITESPACE; }
<COMMENT_DATA> {WHITE_DOC_SPACE_NO_NL}+ / ({COMMENT_END}) {
  boolean isFinalEndToken = braceCount == 1;
  return isFinalEndToken
    ? DOC_WHITESPACE
    : DOC_COMMENT_DATA;
}
<COMMENT_DATA> {WHITE_DOC_SPACE_NO_NL}+ { return DOC_COMMENT_DATA; }
<COMMENT_DATA> [\n\r]+{WHITE_DOC_SPACE_CHAR}* {
  yybegin(COMMENT_DATA_START);
  return DOC_WHITESPACE;
}


<COMMENT_DATA_START, COMMENT_DATA> . {
  yybegin(COMMENT_DATA);
  return DOC_COMMENT_DATA;
}

[^] { return DOC_COMMENT_BAD_CHARACTER; }
