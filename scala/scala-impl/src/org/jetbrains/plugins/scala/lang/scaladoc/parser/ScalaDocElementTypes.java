package org.jetbrains.plugins.scala.lang.scaladoc.parser;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocCommentImpl;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public interface ScalaDocElementTypes {

  /**
   * ScalaDoc comment
   */
  @NotNull
  ILazyParseableElementType SCALA_DOC_COMMENT = new ILazyParseableElementType("SCALA_DOC_COMMENT", ScalaDocLanguage.INSTANCE) {

    @Nullable
    public ASTNode parseContents(@NotNull ASTNode lazyNode) {
      Project project = JavaPsiFacade.getInstance(lazyNode.getTreeParent().getPsi().getProject()).getProject();

      Language language = getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

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
  ScalaDocElementType DOC_LIST = new ScalaDocElementType("ScalaDocList");
  ScalaDocElementType DOC_LIST_ITEM = new ScalaDocElementType("ScalaDocList");

  ScalaDocElementType DOC_PARAM_REF = new ScalaDocElementType("ScalaDocParameterReference");
  ScalaDocElementType DOC_METHOD_REF = new ScalaDocElementType("ScalaDocMethodReference");
  ScalaDocElementType DOC_FIELD_REF = new ScalaDocElementType("ScalaDocFieldReference");
  ScalaDocElementType DOC_METHOD_PARAMS = new ScalaDocElementType("ScalaDocMethodParameterList");
  ScalaDocElementType DOC_METHOD_PARAMETER = new ScalaDocElementType("ScalaDocMethodParameter");

  TokenSet AllElementTypes = TokenSet.create(
          DOC_TAG, DOC_INLINED_TAG, DOC_PARAGRAPH, DOC_LIST, DOC_LIST_ITEM,
          DOC_PARAM_REF, DOC_METHOD_REF, DOC_FIELD_REF, DOC_METHOD_PARAMS, DOC_METHOD_PARAMETER
  );

  TokenSet AllElementAndTokenTypes = TokenSet.orSet(
          AllElementTypes,
          ScalaDocTokenType.ALL_SCALADOC_TOKENS
  );
}
