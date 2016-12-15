package org.jetbrains.sbt.shell;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static org.jetbrains.sbt.shell.SbtShellTypes.*;

%%

%{
  public _SbtShellLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _SbtShellLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s

SPACE=[ \t\n\x0B\f\r]+
ID=[a-zA-Z_0-9]+

%%
<YYINITIAL> {
  {WHITE_SPACE}      { return com.intellij.psi.TokenType.WHITE_SPACE; }

  ":"                { return COLON; }
  "::"               { return DOUBLE_COLON; }
  ";"                { return SEMICOLON; }
  "/"                { return SLASH; }
  "{"                { return OPEN_BRACE; }
  "}"                { return CLOSE_BRACE; }

  {SPACE}            { return SPACE; }
  {ID}               { return ID; }

}

[^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
