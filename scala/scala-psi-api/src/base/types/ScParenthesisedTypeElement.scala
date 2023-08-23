package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScParenthesisedTypeElement extends ScTypeElement with ScParenthesizedElement {
  override protected val typeName = "TypeInParenthesis"

  type Kind = ScTypeElement

  override def innerElement: Option[ScTypeElement] = findChild[ScTypeElement]

  override def sameTreeParent: Option[ScTypeElement] = getParent.asOptionOf[ScTypeElement]
}


object ScParenthesisedTypeElement {
  object InnermostTypeElement {
    def unapply(e: ScalaPsiElement): Option[ScalaPsiElement] = e match {
      case ScParenthesisedTypeElement(InnermostTypeElement(e)) => Some(e)
      case e => Some(e)
    }
  }

  def unapply(e: ScParenthesisedTypeElement): Option[ScTypeElement] = e.innerElement
}