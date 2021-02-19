package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScNamedElementBase, ScTypedDefinition, ScTypedDefinitionBase}

/**
* @author ilyas, Alexander Podkhalyuzin
*/
trait ScSelfTypeElementBase extends ScNamedElementBase with ScTypedDefinitionBase { this: ScSelfTypeElement =>
  def typeElement: Option[ScTypeElement]

  def classNames: Array[String]
}