package org.jetbrains.plugins.scala.lang.scalacli.lexer;

import com.intellij.psi.tree.IElementType;

public interface ScalaCliTokenTypes {
    IElementType tCLI_DIRECTIVE = new ScalaCliElementType("tCLI_DIRECTIVE");
    IElementType tCLI_DIRECTIVE_PREFIX = new ScalaCliElementType("tCLI_DIRECTIVE_PREFIX");
    IElementType tCLI_DIRECTIVE_COMMA = new ScalaCliElementType("tCLI_DIRECTIVE_COMMA");
    IElementType tCLI_DIRECTIVE_COMMAND = new ScalaCliElementType("tCLI_DIRECTIVE_COMMAND");
    IElementType tCLI_DIRECTIVE_KEY = new ScalaCliElementType("tCLI_DIRECTIVE_KEY");
    IElementType tCLI_DIRECTIVE_VALUE = new ScalaCliElementType("tCLI_DIRECTIVE_VALUE");
    IElementType tCLI_DIRECTIVE_TERMINATOR = new ScalaCliElementType("tCLI_DIRECTIVE_TERMINATOR");
    IElementType tCLI_DIRECTIVE_ERROR = new ScalaCliElementType("tCLI_DIRECTIVE_ERROR");
}
