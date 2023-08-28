package org.jetbrains.sbt.shell.grammar;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.jetbrains.sbt.shell.grammar.SbtShellTypes.*;

@SuppressWarnings({"ALL"})
%%

%{
  public _SbtShellLexer() {
    this((java.io.Reader)null);
  }

  public void resetCustom() {
  }
%}

%public
%class _SbtShellLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

ID=[^\s{}:;/]+
URISTRING=\{[A-Za-z0-9._~:/?#@!$&'()*+,;=`.\[\]-]+\}
ANYCHAR=[^;\s]

%%
<YYINITIAL> {
  {WHITE_SPACE}      { return WHITE_SPACE; }

  ":"                { return COLON; }
  "::"               { return DOUBLE_COLON; }
  ";"                { return SEMICOLON; }
  "/"                { return SLASH; }
  "{"                { return OPEN_BRACE; }
  "}"                { return CLOSE_BRACE; }

  {ID}               { return ID; }
  {URISTRING}        { return URISTRING; }
  {ANYCHAR}          { return ANYCHAR; }

}

[^] { return BAD_CHARACTER; }
