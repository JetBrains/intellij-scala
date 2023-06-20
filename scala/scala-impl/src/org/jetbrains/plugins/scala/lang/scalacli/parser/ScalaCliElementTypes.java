package org.jetbrains.plugins.scala.lang.scalacli.parser;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliElementType;
import org.jetbrains.plugins.scala.lang.scalacli.lexer.ScalaCliTokenTypes;
import org.jetbrains.plugins.scala.lang.scalacli.psi.impl.ScCliDirectiveImpl;
import org.jetbrains.plugins.scalaCli.ScalaCliLanguage;

public interface ScalaCliElementTypes {

    /**
     * ScalaCli comment
     */
    @NotNull ILazyParseableElementType SCALA_CLI_DIRECTIVE = new ILazyParseableElementType("SCALA_CLI_DIRECTIVE", ScalaCliLanguage.INSTANCE) {

        @Override
        @Nullable
        public ASTNode parseContents(@NotNull ASTNode lazyNode) {
            Project project = JavaPsiFacade.getInstance(lazyNode.getTreeParent().getPsi().getProject()).getProject();

            Language language = getLanguage();
            ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

            PsiBuilder builder = PsiBuilderFactory.getInstance()
                    .createBuilder(project, lazyNode, parserDefinition.createLexer(project), language, lazyNode.getText());

            ASTNode result = parserDefinition.createParser(project).parse(this, builder);
            ASTNode firstChildNode = result.getFirstChildNode();

            return firstChildNode;
        }

        @Nullable
        @Override
        public ASTNode createNode(CharSequence text) {
            return text != null ? new ScCliDirectiveImpl(text, this) : null;
        }
    };

//    ScalaCliElementType DOC_TAG = new ScalaCliElementType("ScalaCliTag");

//    TokenSet AllElementTypes = TokenSet.create(DOC_TAG);
//
//    TokenSet AllElementAndTokenTypes = TokenSet.orSet(AllElementTypes, ScalaCliTokenTypes.ALL_SCALA_CLI_TOKENS);
}
