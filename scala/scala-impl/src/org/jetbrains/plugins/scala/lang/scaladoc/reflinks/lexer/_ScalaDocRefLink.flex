package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

%%

%class _ScalaDocRefLinkLexer
%implements FlexLexer, ScalaTokenTypes
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

%{
  public _ScalaDocRefLinkLexer() {
    this(null);
  }

  public void resetCustom() {
  }
%}

WS = [ \t\f\r\n]

IDENTIFIER_PART = [^ \t\f\r\n\.#\(\)\[\]`]
ESC = \\ .
IDENTIFIER = ({ESC} | {IDENTIFIER_PART})+

QUOTED_IDENTIFIER = "`" [^`\r\n]* "`"

%%

<YYINITIAL> {
  {WS}+ { return tWHITE_SPACE_IN_LINE; }

  {QUOTED_IDENTIFIER} { return tIDENTIFIER; }
  {IDENTIFIER} { return tIDENTIFIER; }

  "." { return tDOT; }
  "#" { return tINNER_CLASS; }
  "(" { return tLPARENTHESIS; }
  ")" { return tRPARENTHESIS; }
  "[" { return tLSQBRACKET; }
  "]" { return tRSQBRACKET; }

  // Fallback
  . { return tSTUB; }
}
