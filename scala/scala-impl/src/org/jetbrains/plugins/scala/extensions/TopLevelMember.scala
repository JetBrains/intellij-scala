package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

object TopLevelMember {
  def unapply(member: ScMember): Option[(ScMember, ScPackaging)] = member.getContext match {
    case packaging: ScPackaging => Some((member, packaging))
    case _ => None
  }
}
