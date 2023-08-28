package org.jetbrains.plugins.scalaDirective.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tLINE_COMMENT;
import static com.intellij.psi.TokenType.WHITE_SPACE;

@SuppressWarnings({"ALL"})
%%

%class _ScalaDirectiveLexer
%implements FlexLexer, ScalaDirectiveTokenTypes
%public

%function advance
%type IElementType

%{
public void resetCustom() {}
%}

%state FINDING_COMMAND
%state FINDING_KEY
%state FINDING_NEXT_VALUE

NOT_SPACE_OR_COMMA_OR_QUOTATION = [^,\s\"\'`]
VALUE_IN_BACKTICKS              = "`" [^`\r\n]*  "`"
VALUE_IN_DOUBLE_QUOTES          = \"  [^\"\r\n]* \"

DIRECTIVE_PREFIX = "//>"
DIRECTIVE_WHITESPACE =[\s]+
DIRECTIVE_COMMA = [,]
DIRECTIVE_COMMAND = "using"
DIRECTIVE_KEY = {NOT_SPACE_OR_COMMA_OR_QUOTATION}+
                  | {VALUE_IN_BACKTICKS}
DIRECTIVE_VALUE = {NOT_SPACE_OR_COMMA_OR_QUOTATION}+
                    | {VALUE_IN_BACKTICKS}
                    | {VALUE_IN_DOUBLE_QUOTES}

END_OF_LINE_COMMENT="/""/"[^\r\n]*

%%

<YYINITIAL> {DIRECTIVE_PREFIX} {
    yybegin(FINDING_COMMAND);
    return tDIRECTIVE_PREFIX;
}

<FINDING_COMMAND> {DIRECTIVE_COMMAND} {
    yybegin(FINDING_KEY);
    return tDIRECTIVE_COMMAND;
}

<FINDING_KEY, FINDING_NEXT_VALUE> {END_OF_LINE_COMMENT} {
    return tLINE_COMMENT;
}

<FINDING_KEY> {DIRECTIVE_KEY} {
    yybegin(FINDING_NEXT_VALUE);
    return tDIRECTIVE_KEY;
}

<FINDING_NEXT_VALUE> {DIRECTIVE_VALUE} {
    return tDIRECTIVE_VALUE;
}

<FINDING_NEXT_VALUE> {DIRECTIVE_COMMA} {
    return tDIRECTIVE_COMMA;
}

{DIRECTIVE_WHITESPACE} {
    return WHITE_SPACE;
}

[^] { return tDIRECTIVE_ERROR; }
