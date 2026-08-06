package org.jetbrains.plugins.scalaDoc.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Shared parser-definition contract required by the ScalaDoc lazy element type. */
public interface ScalaDocMarkdownAwareParserDefinition extends ParserDefinition {
    @NotNull Lexer createLexerWithFlavour(@NotNull Project project, boolean isMarkdown);

    @NotNull PsiParser createParserWithFlavour(@NotNull Project project, boolean isMarkdown);
    
    void attachMarkdownDataToNode(ASTNode node, PsiBuilder builder);
}
