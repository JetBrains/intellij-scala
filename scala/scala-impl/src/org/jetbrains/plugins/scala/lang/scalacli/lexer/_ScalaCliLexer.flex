package org.jetbrains.plugins.scala.lang.scalacli.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.WHITE_SPACE;

%%

%class _ScalaCliLexer
%implements FlexLexer, ScalaCliTokenTypes
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

CLI_DIRECTIVE_PREFIX = "//>"
CLI_DIRECTIVE_WHITESPACE =[\s]+
CLI_DIRECTIVE_COMMA = [,]
CLI_DIRECTIVE_COMMAND = "using"
CLI_DIRECTIVE_KEY = {NOT_SPACE_OR_COMMA_OR_QUOTATION}+
                  | {VALUE_IN_BACKTICKS}
CLI_DIRECTIVE_VALUE = {NOT_SPACE_OR_COMMA_OR_QUOTATION}+
                    | {VALUE_IN_BACKTICKS}
                    | {VALUE_IN_DOUBLE_QUOTES}


%%

<YYINITIAL> {CLI_DIRECTIVE_PREFIX} {
    yybegin(FINDING_COMMAND);
    return tCLI_DIRECTIVE_PREFIX;
}

<FINDING_COMMAND> {CLI_DIRECTIVE_COMMAND} {
    yybegin(FINDING_KEY);
    return tCLI_DIRECTIVE_COMMAND;
}

<FINDING_KEY> {CLI_DIRECTIVE_KEY} {
    yybegin(FINDING_NEXT_VALUE);
    return tCLI_DIRECTIVE_KEY;
}

<FINDING_NEXT_VALUE> {CLI_DIRECTIVE_VALUE} {
    return tCLI_DIRECTIVE_VALUE;
}

<FINDING_NEXT_VALUE> {CLI_DIRECTIVE_COMMA} {
    return tCLI_DIRECTIVE_COMMA;
}

{CLI_DIRECTIVE_WHITESPACE} {
    return WHITE_SPACE;
}

[^] { return tCLI_DIRECTIVE_ERROR; }
