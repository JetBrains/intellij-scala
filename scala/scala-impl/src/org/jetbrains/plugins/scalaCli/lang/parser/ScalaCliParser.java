package org.jetbrains.plugins.scalaCli.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scalacli.parser.ScalaCliElementTypes;

import static org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliTokenTypes.*;

class ScalaCliParser implements PsiParser, LightPsiParser {

    private boolean finalizeIfTokenTypeIsUnexpectedOrError(IElementType actual, IElementType expected, PsiBuilder.Marker rootMarker, PsiBuilder builder) {
        if (actual != expected || actual == tCLI_DIRECTIVE_ERROR) {
            while (builder.getTokenType() != null) builder.advanceLexer();
            rootMarker.done(ScalaCliElementTypes.SCALA_CLI_DIRECTIVE);
            return true;
        }

        return false;
    }

    @Override
    public void parseLight(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        final PsiBuilder.Marker rootMarker = builder.mark();

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_PREFIX, rootMarker, builder)) return;

        builder.advanceLexer();

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_WHITESPACE, rootMarker, builder)) return;

        builder.advanceLexer();

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_COMMAND, rootMarker, builder)) return;

        final PsiBuilder.Marker commandMarker = builder.mark();
        builder.advanceLexer();
        commandMarker.done(tCLI_DIRECTIVE_COMMAND);

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_WHITESPACE, rootMarker, builder)) return;

        builder.advanceLexer();

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_KEY, rootMarker, builder)) return;

        final PsiBuilder.Marker keyMarker = builder.mark();
        builder.advanceLexer();
        keyMarker.done(tCLI_DIRECTIVE_KEY);

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_WHITESPACE, rootMarker, builder)) return;

        builder.advanceLexer();

        if (finalizeIfTokenTypeIsUnexpectedOrError(builder.getTokenType(), tCLI_DIRECTIVE_VALUE, rootMarker, builder)) return;

        final PsiBuilder.Marker valueMarker = builder.mark();
        builder.advanceLexer();
        valueMarker.done(tCLI_DIRECTIVE_VALUE);

        while (builder.getTokenType() != null) {
            builder.advanceLexer();
        }

        rootMarker.done(root);
    }

    @Override
    @NotNull
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        parseLight(root, builder);
        return builder.getTreeBuilt();
    }
}
