package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScClassParameter extends ScParameter with ScModifierListOwner with ScMember {
  def isVal: Boolean
  def isVar: Boolean
  def isPrivateThis: Boolean

  /** Is the parmameter is explicitly marked as a val or a var; or a case class parameter that is automatically a val. */
  def isEffectiveVal: Boolean = isVal || isVar || isCaseClassVal

  /** Is the parameter automatically a val, due to it's position in a case class parameter list */
  def isCaseClassVal: Boolean = containingClass match {
    case c: ScClass if c.isCase =>
      val isInPrimaryConstructorFirstParamSection = c.constructor match {
        case Some(const) => const.effectiveFirstParameterSection.contains(this)
        case None => false
      }
      val hasExplicitModifier = Option(getModifierList).exists(_.hasExplicitModifiers)
      isInPrimaryConstructorFirstParamSection && !hasExplicitModifier
    case _ => false
  }
}