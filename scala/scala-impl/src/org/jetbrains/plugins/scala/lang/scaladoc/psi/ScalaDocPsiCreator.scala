package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.PsiCreator
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScaladocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl._

/**
* User: Alexander Podkhalyuzin
* Date: 22.07.2008
*/

object ScalaDocPsiCreator extends PsiCreator {
  import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes._
  def createElement(node: ASTNode): PsiElement =
    node.getElementType match {
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
      case ScalaDocTokenType.DOC_INNER_CODE_TAG => new ScDocInnerCodeElementImpl(node)
      case DOC_TAG => new ScDocTagImpl(node)
      case DOC_INLINED_TAG => new ScDocInlinedTagImpl(node)
      case DOC_PARAM_REF => new ScDocParamRefImpl(node)
      case DOC_METHOD_REF => new ScDocMethodRefImpl(node)
      case DOC_FIELD_REF => new ScDocFieldRefImpl(node)
      case DOC_METHOD_PARAMS => new ScDocMethodParamsImpl(node)
      case DOC_METHOD_PARAMETER => new ScDocMethodParameterImpl(node)
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN =>
//        new ScDocTagValueImpl(node)
        var parent = node.getTreeParent

        while (parent != null && parent.getPsi != null && !parent.getPsi.isInstanceOf[ScDocTag]) {
          parent = parent.getTreeParent
        }

        if (parent != null && parent.getPsi != null &&
                parent.getPsi.asInstanceOf[ScDocTag].name == MyScaladocParsing.THROWS_TAG) {
          new ScDocThrowTagValueImpl(node)
        } else {
          new ScDocTagValueImpl(node)
        }
      case ScalaDocTokenType.DOC_CODE_LINK_VALUE => new ScDocResolvableCodeReferenceImpl(node)
    }
}