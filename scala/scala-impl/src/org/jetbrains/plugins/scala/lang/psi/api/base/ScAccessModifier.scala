package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAccessModifierBase extends ScalaPsiElementBase { this: ScAccessModifier =>

  def idText: Option[String]

  def isPrivate: Boolean
  def isProtected: Boolean
  def isThis: Boolean
  def isUnqualifiedPrivateOrThis: Boolean = isPrivate && (getReference == null || isThis)
}