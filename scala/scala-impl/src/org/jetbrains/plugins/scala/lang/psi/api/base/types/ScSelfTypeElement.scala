package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}

trait ScSelfTypeElement extends ScNamedElement with ScTypedDefinition {
  override def nameId: NameId.Placed

  def typeElement: Option[ScTypeElement]

  def classNames: Array[String]
}