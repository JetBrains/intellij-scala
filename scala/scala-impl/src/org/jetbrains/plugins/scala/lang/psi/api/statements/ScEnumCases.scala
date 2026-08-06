package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner

/**
 * Encapsulation of enum cases belonging to a single `case` statement, since one can do
 * {{{
 * enum Number:
 *   case One, Two
 *   case Three
 * }}}
 * To get all 3 cases of the above `enum` use [[org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum.cases]]
 *
 * @note ScalaDoc comment "syntactically belongs" to ScEnumCase[s], not ScEnumCase[].
 *       If there are multiple cases ScalaDoc is simply ignored and no documentation is generated
 */
trait ScEnumCases
  extends ScDeclaredElementsHolder
    with ScModifierListOwner
    with ScAnnotationsHolder
    //Note regarding ScDocCommentOwner: see scaladoc comment
    with ScDocCommentOwner {

  override def declaredElements: Seq[ScEnumCase]
}
