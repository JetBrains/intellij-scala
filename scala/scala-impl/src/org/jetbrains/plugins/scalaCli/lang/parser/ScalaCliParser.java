package org.jetbrains.plugins.scalaCli.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static org.jetbrains.plugins.scalaCli.lang.lexer.ScalaCliTokenTypes.*;

class ScalaCliParser implements PsiParser, LightPsiParser {

    boolean error = false;

    private void processCurrentToken(PsiBuilder builder, IElementType... expected) {

        if (error) {
            builder.advanceLexer();
            return;
        }

        final IElementType currentTokenType = builder.getTokenType();

        if (!Arrays.asList(expected).contains(currentTokenType)) {
            if (Arrays.asList(expected).contains(tCLI_DIRECTIVE_KEY)) {
                builder.error("Scala CLI key expected: option, dep, jar, etc.");
            } else {
                builder.error("Unexpected token");
            }
            error = true;
        }

        builder.advanceLexer();
    }

    @Override
    public void parseLight(@NotNull IElementType root, @NotNull PsiBuilder builder) {

        final PsiBuilder.Marker rootMarker = builder.mark();

        processCurrentToken(builder, tCLI_DIRECTIVE_PREFIX);
        processCurrentToken(builder, tCLI_DIRECTIVE_COMMAND);
        processCurrentToken(builder, tCLI_DIRECTIVE_KEY);

        while (builder.getTokenType() != null) {
            processCurrentToken(builder, tCLI_DIRECTIVE_VALUE, tCLI_DIRECTIVE_COMMA);
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
