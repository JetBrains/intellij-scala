package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi

import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import impl._
import parser.ScalaDocElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import lexer.docsyntax.ScaladocSyntaxElementType
import lexer.ScalaDocTokenType

/**
* User: Alexander Podkhalyuzin
* Date: 22.07.2008
*/

object ScalaDocPsiCreator {
  import ScalaDocElementTypes._
  def createElement(node: ASTNode): PsiElement =
    node.getElementType match {
      case a: ScaladocSyntaxElementType => 
        val element = new ScDocSyntaxElementImpl(node)
        element.setFlag(a.getFlagConst)

        var parrentNode = node
        while (parrentNode != null && parrentNode.getElementType != SCALA_DOC_COMMENT) {
          parrentNode = parrentNode.getTreeParent
          if (parrentNode.getElementType.isInstanceOf[ScaladocSyntaxElementType]){
            element.setFlag(parrentNode.getElementType.asInstanceOf[ScaladocSyntaxElementType].getFlagConst)
          }
        }

        element
      case ScalaDocTokenType.DOC_INNER_CODE_TAG => new ScDocInnerCodeElementImpl(node)
      case DOC_TAG => new ScDocTagImpl(node)
      case DOC_INLINED_TAG => new ScDocInlinedTagImpl(node)
      case DOC_REFERENCE_ELEMENT => new ScDocReferenceElementImpl(node)
      case DOC_PARAM_REF => new ScDocParamRefImpl(node)
      case DOC_METHOD_REF => new ScDocMethodRefImpl(node)
      case DOC_FIELD_REF => new ScDocFieldRefImpl(node)
      case DOC_METHOD_PARAMS => new ScDocMethodParamsImpl(node)
      case DOC_METHOD_PARAMETER => new ScDocMethodParameterImpl(node)
    }
}