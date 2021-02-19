package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.extensions.ObjectExt

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParenthesisedTypeElementBase extends ScTypeElementBase with ScParenthesizedElementBase { this: ScParenthesisedTypeElement =>
  override protected val typeName = "TypeInParenthesis"

  type Kind = ScTypeElement

  override def innerElement: Option[ScTypeElement] = findChild[ScTypeElement]

  override def sameTreeParent: Option[ScTypeElement] = getParent.asOptionOf[ScTypeElement]
}


abstract class ScParenthesisedTypeElementCompanion {
  def unapply(e: ScParenthesisedTypeElement): Option[ScTypeElement] = e.innerElement
}