package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParenthesisedTypeElement extends ScTypeElement with ScGenericParenthesisedNode[ScTypeElement] {
  override protected val typeName = "TypeInParenthesis"


  def typeElement: Option[ScTypeElement] = findChild(classOf[ScTypeElement])

  override def subNode: Option[ScTypeElement] = typeElement

  override def isSameTree(p: PsiElement): Boolean = p.isInstanceOf[ScTypeElement]

  override def isParenthesisClarifying: Boolean = {
    (getParent, typeElement) match {
      case (p: ScTypeElement, Some(c)) if !isIndivisible(c) && getPrecedence(p) != getPrecedence(c) => true
      case _ => false
    }
  }

  override protected def getPrecedence(typeElem: ScTypeElement): Int = typeElem match {
    case _: ScParameterizedTypeElement | _: ScTypeProjection | _: ScSimpleTypeElement | _: ScTupleTypeElement | _: ScParenthesisedTypeElement => 0
    case _: ScAnnotTypeElement => 1
    case _: ScCompoundTypeElement => 2
    case _: ScInfixTypeElement => 3
    case _: ScExistentialTypeElement => 4
    case _: ScWildcardTypeElement => 5
    case _: ScFunctionalTypeElement => 6
    case _ => throw new IllegalArgumentException(s"Unknown type element $typeElem")
  }
}


object ScParenthesisedTypeElement {
  def unapply(e: ScParenthesisedTypeElement): Option[ScTypeElement] = e.typeElement
}