package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.ScModifierListOwner
import toplevel.typedef.{ScClass, ScMember}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScClassParameter extends ScParameter with ScModifierListOwner with ScMember {
  def isVal : Boolean
  def isVar : Boolean

  /** Is the parmameter is explicitly marked as a val or a var; or a case class parameter that is automatically a val. */
  def isEffectiveVal = isVal || isVar || isCaseClassVal

  /** Is the parameter automatically a val, due to it's position in a case class parameter list */
  def isCaseClassVal = getContainingClass match {
    case c: ScClass if c.isCase =>
      val inPrimaryParamClause = c.allClauses.take(1).exists(pc => !pc.isImplicit && pc.parameters.contains(this))
      val hasExplicitModifier = Option(getModifierList).exists(_.hasExplicitModifiers)
      inPrimaryParamClause && !hasExplicitModifier
    case _ => false
  }
}