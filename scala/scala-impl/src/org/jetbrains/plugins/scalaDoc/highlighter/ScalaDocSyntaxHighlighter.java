package org.jetbrains.plugins.scalaDoc.highlighter;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;

import java.util.Collections;
import java.util.Map;

public final class ScalaDocSyntaxHighlighter extends SyntaxHighlighterBase {

    @NotNull
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES =
            Collections.singletonMap(ScalaDocTokenType.DOC_TAG_NAME, DefaultHighlighter.SCALA_DOC_TAG);

    public ScalaDocSyntaxHighlighter() {
        super();
    }

    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
        return LanguageParserDefinitions.INSTANCE
                .forLanguage(ScalaDocLanguage.INSTANCE)
                .createLexer(null);
    }

    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(ATTRIBUTES.get(tokenType));
    }
}
