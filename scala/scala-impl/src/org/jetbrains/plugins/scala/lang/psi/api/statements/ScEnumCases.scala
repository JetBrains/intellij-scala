package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
 * Encapsulation of enum cases belonging to a single `case` statement, since one can do
 * {{{
 * enum Number:
 *   case One, Two
 *   case Three
 * }}}
 * To get all 3 cases of the above `enum` use [[org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum.cases]]
 */
trait ScEnumCases extends ScDeclaredElementsHolder with ScModifierListOwner {
  override def declaredElements: Seq[ScEnumCase]
}
