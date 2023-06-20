package org.jetbrains.plugins.scala.lang.scalacli.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliTokenTypes;

%%

%class ScalaCliLexer
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
%state FINDING_VALUES
%state VALUE_FOUND

CLI_DIRECTIVE_PREFIX = "//>"
CLI_DIRECTIVE_WHITESPACE =[\s]+
CLI_DIRECTIVE_COMMAND = [a-z]+
CLI_DIRECTIVE_KEY = [a-z.]+
CLI_DIRECTIVE_VALUE = [a-z.:0-9]+
CLI_DIRECTIVE_TERMINATOR = (\n|\r\n)

%%

<YYINITIAL> {CLI_DIRECTIVE_PREFIX} {
    yybegin(PREFIX_FOUND);
    return tCLI_DIRECTIVE_PREFIX;
}

<PREFIX_FOUND, COMMAND_FOUND, KEY_FOUND, VALUE_FOUND> {CLI_DIRECTIVE_WHITESPACE} {
    switch (yystate()) {
        case PREFIX_FOUND -> yybegin(FINDING_COMMAND);
        case COMMAND_FOUND -> yybegin(FINDING_KEY);
        case KEY_FOUND -> yybegin(FINDING_VALUES);
        case VALUE_FOUND -> yybegin(FINDING_VALUES);
    }
    return tCLI_DIRECTIVE_WHITESPACE;
}

<FINDING_COMMAND> {CLI_DIRECTIVE_COMMAND} {
    yybegin(COMMAND_FOUND);
    return tCLI_DIRECTIVE_COMMAND;
}

<FINDING_KEY> {CLI_DIRECTIVE_KEY} {
    yybegin(KEY_FOUND);
    return tCLI_DIRECTIVE_KEY;
}

<FINDING_VALUES> {CLI_DIRECTIVE_VALUE} {
    yybegin(VALUE_FOUND);
    return tCLI_DIRECTIVE_VALUE;
}

[^] { return tCLI_DIRECTIVE_ERROR; }
