package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}

trait ScSelfTypeElement extends ScNamedElement with ScTypedDefinition {
  def typeElement: Option[ScTypeElement]

  def classNames: Array[String]
}