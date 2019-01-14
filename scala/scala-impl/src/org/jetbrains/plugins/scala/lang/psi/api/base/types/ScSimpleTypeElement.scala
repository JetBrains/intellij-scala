package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationExpr
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Author: Alexander Podkhalyuzin
 * Date: 22.02.2008
 */
trait ScSimpleTypeElement extends ScTypeElement {
  override protected val typeName = "SimpleType"

  def reference: Option[ScStableCodeReferenceElement] = findChild(classOf[ScStableCodeReferenceElement])
  def pathElement: ScPathElement = findChildByClassScala(classOf[ScPathElement])

  def singleton: Boolean = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  def annotation: Boolean = ScalaPsiUtil.getContext(this, 2).exists(_.isInstanceOf[ScAnnotationExpr])

  def findConstructor: Option[ScConstructor] = {
    def findConstructor(element: ScalaPsiElement) = element.getContext match {
      case constructor: ScConstructor => Some(constructor)
      case _ => None
    }

    getContext match {
      case typeElement: ScParameterizedTypeElement => findConstructor(typeElement)
      case _ => findConstructor(this)
    }
  }
}

object ScSimpleTypeElement {
  def unapply(te: ScSimpleTypeElement): Option[Option[ScStableCodeReferenceElement]] = Some(te.reference)
}