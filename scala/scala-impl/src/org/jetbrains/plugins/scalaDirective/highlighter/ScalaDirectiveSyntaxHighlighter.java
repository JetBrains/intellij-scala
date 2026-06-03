package org.jetbrains.plugins.scalaDirective.highlighter;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes;
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage;

import java.util.HashMap;
import java.util.Map;

public final class ScalaDirectiveSyntaxHighlighter extends SyntaxHighlighterBase {

    @NotNull
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES;

    static {
        ATTRIBUTES = new HashMap<>();
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_PREFIX, DefaultHighlighter.SCALA_DIRECTIVE_PREFIX);
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_COMMAND, DefaultHighlighter.SCALA_DIRECTIVE_COMMAND);
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_KEY, DefaultHighlighter.SCALA_DIRECTIVE_KEY);
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE, DefaultHighlighter.SCALA_DIRECTIVE_VALUE);
    }

    public ScalaDirectiveSyntaxHighlighter() {
        super();
    }

    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
        return LanguageParserDefinitions.INSTANCE
                .forLanguage(ScalaDirectiveLanguage.INSTANCE)
                .createLexer(null);
    }

    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(ATTRIBUTES.get(tokenType));
    }
}
