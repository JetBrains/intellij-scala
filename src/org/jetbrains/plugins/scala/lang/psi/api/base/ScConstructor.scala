package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScConstructor extends ScalaPsiElement {
  def typeElement: ScTypeElement

  def simpleTypeElement: Option[ScSimpleTypeElement]

  def typeArgList: Option[ScTypeArgs] = typeElement match {
    case x: ScParameterizedTypeElement => Some(x.typeArgList)
    case _ => None
  }

  def args = findChild(classOf[ScArgumentExprList])

  def arguments: Seq[ScArgumentExprList] =
    Seq(findChildrenByClassScala(classOf[ScArgumentExprList]): _*)

  def expectedType: Option[ScType]

  def newTemplate: Option[ScNewTemplateDefinition]

  def shapeType(i: Int): TypeResult[ScType]

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]]
  
  def multiType(i: Int): Seq[TypeResult[ScType]]

  def reference: Option[ScStableCodeReferenceElement]
}

object ScConstructor {
  def unapply(c: ScConstructor): Option[(ScTypeElement, Seq[ScArgumentExprList])] = {
    Option(c).map(it => (it.typeElement, it.arguments))
  }

  object byReference {
    def unapply(ref: ScReferenceElement): Option[ScConstructor] = {
      PsiTreeUtil.getParentOfType(ref, classOf[ScConstructor]) match {
        case null => None
        case c if c.reference == Some(ref) => Some(c)
        case _ => None
      }
    }
  }
}

