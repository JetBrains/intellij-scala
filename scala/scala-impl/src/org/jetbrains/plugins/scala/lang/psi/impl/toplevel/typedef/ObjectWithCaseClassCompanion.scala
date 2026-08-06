package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}

object ObjectWithCaseClassCompanion {
  def unapply(td: ScTypeDefinition): Option[(ScObject, ScClass)] = td match {
    case obj: ScObject =>
      ScalaPsiUtil.getCompanionModule(obj).collect {
        case c: ScClass if c.isCase => (obj, c)
      }
    case _ => None
  }
}
