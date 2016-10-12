package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}

/**
* @author ilyas, Alexander Podkhalyuzin
*/
trait ScSelfTypeElement extends ScNamedElement with ScTypedDefinition {
  def typeElement: Option[ScTypeElement]

  def classNames: Array[String]
}