package org.jetbrains.plugins.scalaDirective.lang.parser;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage;

public interface ScalaDirectiveElementTypes {

    @NotNull ILazyParseableElementType SCALA_DIRECTIVE = new ILazyParseableElementType("SCALA_DIRECTIVE", ScalaDirectiveLanguage.INSTANCE) {

        @Override
        @Nullable
        public ASTNode parseContents(@NotNull ASTNode lazyNode) {
            Project project = lazyNode.getTreeParent().getPsi().getProject();

            Language language = getLanguage();
            ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

            PsiBuilder builder = PsiBuilderFactory.getInstance()
                    .createBuilder(project, lazyNode, parserDefinition.createLexer(project), language, lazyNode.getText());

            ASTNode result = parserDefinition.createParser(project).parse(this, builder);

            return result.getFirstChildNode();
        }
    };
}
