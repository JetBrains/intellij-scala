package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * Author: Alexander Podkhalyuzin
 * Date: 22.02.2008
 */
trait ScSimpleTypeElement extends ScTypeElement {
  override protected val typeName = "SimpleType"

  def reference: Option[ScStableCodeReference] = findChild(classOf[ScStableCodeReference])
  def pathElement: ScPathElement = findChildByClassScala(classOf[ScPathElement])

  def singleton: Boolean = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  def annotation: Boolean = ScalaPsiUtil.getContext(this, 2).exists(_.isInstanceOf[ScAnnotationExpr])

  def findConstructorInvocation: Option[ScConstructorInvocation] = {
    val constrInvocationTypeElement = getContext match {
      case typeElement: ScParameterizedTypeElement => typeElement
      case _ => this
    }

    constrInvocationTypeElement.getContext match {
      case constrInvocation: ScConstructorInvocation => Some(constrInvocation)
      case _ => None
    }
  }
}

object ScSimpleTypeElement {
  def unapply(te: ScSimpleTypeElement): Option[Option[ScStableCodeReference]] = Some(te.reference)
}