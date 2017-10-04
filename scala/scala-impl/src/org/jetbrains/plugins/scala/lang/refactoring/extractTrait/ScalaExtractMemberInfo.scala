package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberInfoBase

/**
 * Nikolay.Tropin
 * 2014-05-23
 */
class ScalaExtractMemberInfo(member: ScMember) extends ScalaMemberInfoBase[ScMember](member: ScMember)

object ScalaExtractMemberInfo {
  def unapply(info: ScalaExtractMemberInfo): Option[(ScMember, Boolean)] = Some((info.getMember, info.isToAbstract))
}
