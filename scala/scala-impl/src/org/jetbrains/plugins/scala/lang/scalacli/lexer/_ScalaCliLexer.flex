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

%state PREFIX_FOUND
%state FINDING_COMMAND
%state COMMAND_FOUND
%state FINDING_KEY
%state KEY_FOUND
%state FINDING_NEXT_VALUE
%state VALUE_FOUND

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
    yybegin(PREFIX_FOUND);
    return tCLI_DIRECTIVE_PREFIX;
}

<YYINITIAL, PREFIX_FOUND, COMMAND_FOUND, KEY_FOUND> {CLI_DIRECTIVE_WHITESPACE} {
    switch (yystate()) {
        case PREFIX_FOUND -> yybegin(FINDING_COMMAND);
        case COMMAND_FOUND -> yybegin(FINDING_KEY);
        case KEY_FOUND -> yybegin(FINDING_NEXT_VALUE);
    }
    return WHITE_SPACE;
}

<KEY_FOUND> {CLI_DIRECTIVE_COMMA} {
    yybegin(FINDING_NEXT_VALUE);
    return tCLI_DIRECTIVE_COMMA;
}

<PREFIX_FOUND, FINDING_COMMAND> {CLI_DIRECTIVE_COMMAND} {
    yybegin(COMMAND_FOUND);
    return tCLI_DIRECTIVE_COMMAND;
}

<FINDING_KEY> {CLI_DIRECTIVE_KEY} {
    yybegin(KEY_FOUND);
    return tCLI_DIRECTIVE_KEY;
}

<FINDING_NEXT_VALUE> {CLI_DIRECTIVE_VALUE} {
    yybegin(VALUE_FOUND);
    return tCLI_DIRECTIVE_VALUE;
}

<FINDING_NEXT_VALUE> {CLI_DIRECTIVE_COMMA} {
    return tCLI_DIRECTIVE_COMMA;
}

<FINDING_NEXT_VALUE> {CLI_DIRECTIVE_WHITESPACE} {
    return WHITE_SPACE;
}

<VALUE_FOUND> {CLI_DIRECTIVE_WHITESPACE} {
    yybegin(FINDING_NEXT_VALUE);
    return WHITE_SPACE;
}

<VALUE_FOUND> {CLI_DIRECTIVE_COMMA} {
    yybegin(FINDING_NEXT_VALUE);
    return tCLI_DIRECTIVE_COMMA;
}

[^] { return tCLI_DIRECTIVE_ERROR; }
