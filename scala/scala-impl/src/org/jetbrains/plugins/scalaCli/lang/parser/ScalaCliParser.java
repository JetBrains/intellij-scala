package org.jetbrains.plugins.scalaCli.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliTokenTypes.*;

class ScalaCliParser implements PsiParser, LightPsiParser {

    private void processCurrentToken(PsiBuilder builder, IElementType expected) {

        final IElementType currentTokenType = builder.getTokenType();

        if (currentTokenType == expected) {
            includeCurrentToken(builder);
        } else if (currentTokenType != null) {
            // The current token is an error token, or it is unexpected, in which case we want to parse the
            // current token as an an error token too.
            includeToken(builder, tCLI_DIRECTIVE_ERROR);
        }
    }

    private void includeCurrentToken(PsiBuilder builder) {
        includeToken(builder, builder.getTokenType());
    }

    private void includeToken(PsiBuilder builder, IElementType tokenType) {
        final PsiBuilder.Marker commandMarker = builder.mark();
        builder.advanceLexer();
        commandMarker.done(tokenType);
    }

    @Override
    public void parseLight(@NotNull IElementType root, @NotNull PsiBuilder builder) {

        final PsiBuilder.Marker rootMarker = builder.mark();

        processCurrentToken(builder, tCLI_DIRECTIVE_PREFIX);
        processCurrentToken(builder, tCLI_DIRECTIVE_COMMAND);
        processCurrentToken(builder, tCLI_DIRECTIVE_KEY);

        while (builder.getTokenType() != null) {
            processCurrentToken(builder, tCLI_DIRECTIVE_VALUE);
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
