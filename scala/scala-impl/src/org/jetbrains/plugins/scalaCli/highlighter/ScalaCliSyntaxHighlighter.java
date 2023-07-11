package org.jetbrains.plugins.scalaCli.highlighter;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.scalaCli.lang.lexer.ScalaCliTokenTypes;
import org.jetbrains.plugins.scalaCli.ScalaCliLanguage;

import java.util.HashMap;
import java.util.Map;

public final class ScalaCliSyntaxHighlighter extends SyntaxHighlighterBase {

    @NotNull
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES;

    static {
        ATTRIBUTES = new HashMap<>();
        ATTRIBUTES.put(ScalaCliTokenTypes.tCLI_DIRECTIVE_PREFIX, DefaultHighlighter.SCALA_CLI_DIRECTIVE_PREFIX);
        ATTRIBUTES.put(ScalaCliTokenTypes.tCLI_DIRECTIVE_COMMAND, DefaultHighlighter.SCALA_CLI_DIRECTIVE_COMMAND);
        ATTRIBUTES.put(ScalaCliTokenTypes.tCLI_DIRECTIVE_KEY, DefaultHighlighter.SCALA_CLI_DIRECTIVE_KEY);
        ATTRIBUTES.put(ScalaCliTokenTypes.tCLI_DIRECTIVE_VALUE, DefaultHighlighter.SCALA_CLI_DIRECTIVE_VALUE);
    }

    public ScalaCliSyntaxHighlighter() {
        super();
    }

    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
        return LanguageParserDefinitions.INSTANCE
                .forLanguage(ScalaCliLanguage.INSTANCE)
                .createLexer(null);
    }

    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(ATTRIBUTES.get(tokenType));
    }
}
