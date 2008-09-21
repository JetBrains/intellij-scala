package org.jetbrains.plugins.scala.lang.scaladoc.psi

import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import parser.ScalaDocElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import impl._

/**
* User: Alexander Podkhalyuzin
* Date: 22.07.2008
*/

object ScalaDocPsiCreator {
  def createElement(node: ASTNode): PsiElement =
    node.getElementType match {
      case ScalaDocElementTypes.DOC_TAG => new ScDocTagImpl(node)
      case ScalaDocElementTypes.DOC_INLINED_TAG => new ScDocInlinedTagImpl(node)
      case ScalaDocElementTypes.DOC_REFERENCE_ELEMENT => new ScDocReferenceElementImpl(node)
      case ScalaDocElementTypes.DOC_PARAM_REF => new ScDocParamRefImpl(node)
      case ScalaDocElementTypes.DOC_METHOD_REF => new ScDocMethodRefImpl(node)
      case ScalaDocElementTypes.DOC_FIELD_REF => new ScDocFieldRefImpl(node)
      case ScalaDocElementTypes.DOC_METHOD_PARAMS => new ScDocMethodParamsImpl(node)
      case ScalaDocElementTypes.DOC_METHOD_PARAMETER => new ScDocMethodParameterImpl(node)
    }
}