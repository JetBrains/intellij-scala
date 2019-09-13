package org.jetbrains.plugins.scala.lang.scaladoc.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;

import java.util.Collections;
import java.util.Map;

import static org.jetbrains.plugins.scala.highlighter.DefaultHighlighter.SCALA_DOC_TAG;
import static org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType.DOC_TAG_NAME;

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.07.2008
 */
public final class ScalaDocSyntaxHighlighter extends SyntaxHighlighterBase {

    @NotNull
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES =
            Collections.singletonMap(DOC_TAG_NAME, SCALA_DOC_TAG);

    @NotNull
    public Lexer getHighlightingLexer() {
        return new ScalaDocLexer();
    }

    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(ATTRIBUTES.get(tokenType));
    }
}
