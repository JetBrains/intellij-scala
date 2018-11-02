package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.07.2008
  */
object ScalaDocPsiCreator {

  import lexer.ScalaDocTokenType._
  import parser.ScalaDocElementTypes._
  import psi.impl._

  def createElement(node: ASTNode,
                    elementType: lexer.ScalaDocElementType): PsiElement = elementType match {
    case a: ScaladocSyntaxElementType =>
      val element = new ScDocSyntaxElementImpl(node)
      element.setFlag(a.getFlagConst)

      var parentNode = node
      while (parentNode.getTreeParent != null && parentNode.getElementType != SCALA_DOC_COMMENT) {
        parentNode = parentNode.getTreeParent
        parentNode.getElementType match {
          case a: ScaladocSyntaxElementType =>
            element.setFlag(a.getFlagConst)
          case _ =>
        }
      }

      element
    case DOC_INNER_CODE_TAG => new ScDocInnerCodeElementImpl(node)
    case DOC_TAG => new ScDocTagImpl(node)
    case DOC_INLINED_TAG => new ScDocInlinedTagImpl(node)
    case DOC_PARAM_REF => new ScDocParamRefImpl(node)
    case DOC_METHOD_REF => new ScDocMethodRefImpl(node)
    case DOC_FIELD_REF => new ScDocFieldRefImpl(node)
    case DOC_METHOD_PARAMS => new ScDocMethodParamsImpl(node)
    case DOC_METHOD_PARAMETER => new ScDocMethodParameterImpl(node)
    case DOC_TAG_VALUE_TOKEN =>
      //        new ScDocTagValueImpl(node)
      var parent = node.getTreeParent

      while (parent != null && parent.getPsi != null && !parent.getPsi.isInstanceOf[api.ScDocTag]) {
        parent = parent.getTreeParent
      }

      if (parent != null && parent.getPsi != null &&
        parent.getPsi.asInstanceOf[api.ScDocTag].name == MyScaladocParsing.THROWS_TAG) {
        new ScDocThrowTagValueImpl(node)
      } else {
        new ScDocTagValueImpl(node)
      }
    case DOC_CODE_LINK_VALUE => new ScDocResolvableCodeReferenceImpl(node)
  }
}