package org.jetbrains.plugins.scala.lang.scaladoc.parser;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType;
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocCommentImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
public interface ScalaDocElementTypes extends ScalaDocTokenType{
  /**
   * ScalaDoc comment
   */
  ILazyParseableElementType SCALA_DOC_COMMENT = new ILazyParseableElementType("SCALA_DOC_COMMENT", ScalaFileType.SCALA_FILE_TYPE.getLanguage()) {

    public ASTNode parseContents(ASTNode lazyNode) {
      final PsiElement parentElement = lazyNode.getTreeParent().getPsi();
      final Project project = JavaPsiFacade.getInstance(parentElement.getProject()).getProject();

      final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, lazyNode, new ScalaDocLexer(), getLanguage(), lazyNode.getText());
      final PsiParser parser = new ScalaDocParser();

      return parser.parse(this, builder).getFirstChildNode();
    }

    @Override
     public ASTNode createNode(CharSequence text) {
      return new ScDocCommentImpl(text);
    }
  };

  ScalaDocElementType DOC_TAG = new ScalaDocElementType("ScalaDocTag");
  ScalaDocElementType DOC_INLINED_TAG = new ScalaDocElementType("ScalaDocInlinedTag");

  ScalaDocElementType DOC_REFERENCE_ELEMENT = new ScalaDocElementType("ScalaDocReferenceElement");
  ScalaDocElementType DOC_PARAM_REF = new ScalaDocElementType("ScalaDocParameterReference");
  ScalaDocElementType DOC_METHOD_REF = new ScalaDocElementType("ScalaDocMethodReference");
  ScalaDocElementType DOC_FIELD_REF = new ScalaDocElementType("ScalaDocFieldReference");
  ScalaDocElementType DOC_METHOD_PARAMS = new ScalaDocElementType("ScalaDocMethodParameterList");
  ScalaDocElementType DOC_METHOD_PARAMETER = new ScalaDocElementType("ScalaDocMethodParameter");
}
