package org.jetbrains.plugins.scalaDirective.highlighter;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes;
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage;

import java.util.HashMap;
import java.util.Map;

public final class ScalaDirectiveSyntaxHighlighter extends SyntaxHighlighterBase {

    private static TextAttributesKey createKey(@NonNls @NotNull String externalName, TextAttributesKey prototype) {
        return TextAttributesKey.createTextAttributesKey(externalName, prototype);
    }

    private static final String SCALA_DIRECTIVE_PREFIX_ID = "Scala directive prefix";
    private static final String SCALA_DIRECTIVE_COMMAND_ID = "Scala directive command";
    private static final String SCALA_DIRECTIVE_KEY_ID = "Scala directive key";
    private static final String SCALA_DIRECTIVE_VALUE_ID = "Scala directive value";

    public static final TextAttributesKey SCALA_DIRECTIVE_PREFIX = createKey(SCALA_DIRECTIVE_PREFIX_ID, DefaultLanguageHighlighterColors.DOC_COMMENT);
    public static final TextAttributesKey SCALA_DIRECTIVE_COMMAND = createKey(SCALA_DIRECTIVE_COMMAND_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
    public static final TextAttributesKey SCALA_DIRECTIVE_KEY = createKey(SCALA_DIRECTIVE_KEY_ID, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE);
    public static final TextAttributesKey SCALA_DIRECTIVE_VALUE = createKey(SCALA_DIRECTIVE_VALUE_ID, DefaultLanguageHighlighterColors.DOC_COMMENT);

    @NotNull
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES;

    static {
        ATTRIBUTES = new HashMap<>();
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_PREFIX, SCALA_DIRECTIVE_PREFIX);
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_COMMAND, SCALA_DIRECTIVE_COMMAND);
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_KEY, SCALA_DIRECTIVE_KEY);
        ATTRIBUTES.put(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE, SCALA_DIRECTIVE_VALUE);
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
