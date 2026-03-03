package org.jetbrains.plugins.scala.lang.scaladoc.parser;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.Scala3Language;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.ScaladocMarkdownParsing;
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocCommentImpl;
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocReferenceLinkImpl;
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.ScalaDocRefLinkLanguage;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;
import org.jetbrains.plugins.scalaDoc.lang.parser.ScalaDocParserDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ScalaDocElementTypes {
    @NotNull
    ILazyParseableElementType SCALA_DOC_REFERENCE_LINK = new ILazyParseableElementType("SCALA_DOC_REFERENCE_LINK", ScalaDocRefLinkLanguage.INSTANCE()) {
        @Override
        @Nullable
        public ASTNode parseContents(@NotNull ASTNode lazyNode) {
            PsiElement psi = lazyNode.getTreeParent().getPsi();

            Project project = psi.getProject();
            Language language = getLanguage();
            ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
            Lexer lexer = parserDefinition.createLexer(project);
            PsiParser parser = parserDefinition.createParser(project);
            PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, lazyNode, lexer, language, lazyNode.getChars());
            parser.parse(ScalaDocElementTypes.SCALA_DOC_REFERENCE_LINK, builder);
            return builder.getTreeBuilt().getFirstChildNode();
        }

        @Nullable
        @Override
        public ASTNode createNode(CharSequence text) {
            return text != null ?
                    new ScDocReferenceLinkImpl(this, text) :
                    null;
        }
    };

    /**
     * ScalaDoc comment
     * <p>
     * See similar element for Java: {@link com.intellij.psi.impl.source.tree.JavaDocElementType#DOC_COMMENT}
     * (though it's implemented slightly differently at the moment, e.g., there is no separate JavaDocLanguage)
     */
    @NotNull
    ILazyParseableElementType SCALA_DOC_COMMENT = new ILazyParseableElementType("SCALA_DOC_COMMENT", ScalaDocLanguage.INSTANCE) {

        @Override
        @Nullable
        public ASTNode parseContents(@NotNull ASTNode lazyNode) {
            Project project = JavaPsiFacade.getInstance(lazyNode.getTreeParent().getPsi().getProject()).getProject();

            Language language = getLanguage();
            ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

            if (parserDefinition instanceof ScalaDocParserDefinition) {
                boolean isMarkdown = isMarkdown(lazyNode);

                PsiBuilder builder = PsiBuilderFactory.getInstance()
                        .createBuilder(
                                project,
                                lazyNode,
                                ((ScalaDocParserDefinition) parserDefinition).createLexerWithFlavour(project, isMarkdown),
                                language,
                                lazyNode.getText()
                        );

                ASTNode node = ((ScalaDocParserDefinition) parserDefinition)
                        .createParserWithFlavour(project, isMarkdown)
                        .parse(this, builder)
                        .getFirstChildNode();

                // Hand Markdown text over to the first child node
                // We can't put it on the parent because it doesn't exist yet
                node.putUserData(ScaladocMarkdownParsing.MARKDOWN_DATA(), builder.getUserData(ScaladocMarkdownParsing.MARKDOWN_DATA()));
                return node;
            } else {
                PsiBuilder builder = PsiBuilderFactory.getInstance()
                        .createBuilder(
                                project,
                                lazyNode,
                                parserDefinition.createLexer(project),
                                language,
                                lazyNode.getText()
                        );

                return parserDefinition
                        .createParser(project)
                        .parse(this, builder)
                        .getFirstChildNode();
            }
        }

        private static final Pattern SYNTAX_TAG = Pattern.compile("^\\s*\\*?\\s*@syntax\\s+(\\S+)\\s*$", Pattern.MULTILINE);

        private boolean isMarkdown(@NotNull ASTNode lazyNode) {
            Matcher syntaxMatcher = SYNTAX_TAG.matcher(lazyNode.getText());
            if (syntaxMatcher.find()) {
                String syntaxType = syntaxMatcher.group(1);
                if (syntaxType.equals("markdown")) {
                    return true;
                } else if (syntaxType.equals("wiki")) {
                    return false;
                }
            }

            // Can't use this, it's a Java file :(
            // lazyNode.getTreeParent().getPsi().getContainingFile().isScala3File

            // This works, though!
            return lazyNode
                    .getTreeParent()
                    .getPsi()
                    .getContainingFile()
                    .getLanguage()
                    .isKindOf(Scala3Language.INSTANCE);
        }

        @Nullable
        @Override
        public ASTNode createNode(CharSequence text) {
            return text != null ?
                    new ScDocCommentImpl(text, this) :
                    null;
        }
    };

    ScalaDocElementType DOC_TAG = new ScalaDocElementType("ScalaDocTag");
    ScalaDocElementType DOC_INLINED_TAG = new ScalaDocElementType("ScalaDocInlinedTag");
    ScalaDocElementType DOC_PARAGRAPH = new ScalaDocElementType("ScalaDocParagraph");
    ScalaDocElementType DOC_CODEBLOCK = new ScalaDocElementType("ScalaDocCodeBlock");
    ScalaDocElementType DOC_LIST = new ScalaDocElementType("ScalaDocList");
    ScalaDocElementType DOC_LIST_ITEM = new ScalaDocElementType("ScalaDocList");

    // Markdown-specific elements
    ScalaDocElementType DOC_BLOCKQUOTE = new ScalaDocElementType("ScalaDocBlockquote");

    ScalaDocElementType DOC_PARAM_REF = new ScalaDocElementType("ScalaDocParameterReference");
    ScalaDocElementType DOC_METHOD_REF = new ScalaDocElementType("ScalaDocMethodReference");
    ScalaDocElementType DOC_FIELD_REF = new ScalaDocElementType("ScalaDocFieldReference");
    ScalaDocElementType DOC_METHOD_PARAMS = new ScalaDocElementType("ScalaDocMethodParameterList");
    ScalaDocElementType DOC_METHOD_PARAMETER = new ScalaDocElementType("ScalaDocMethodParameter");

    TokenSet AllElementTypes = TokenSet.create(
            DOC_TAG, DOC_INLINED_TAG, DOC_PARAGRAPH, DOC_CODEBLOCK, DOC_LIST, DOC_LIST_ITEM,
            DOC_PARAM_REF, DOC_METHOD_REF, DOC_FIELD_REF, DOC_METHOD_PARAMS, DOC_METHOD_PARAMETER
    );

    TokenSet AllElementAndTokenTypes = TokenSet.orSet(
            AllElementTypes,
            ScalaDocTokenType.ALL_SCALADOC_TOKENS
    );
}
