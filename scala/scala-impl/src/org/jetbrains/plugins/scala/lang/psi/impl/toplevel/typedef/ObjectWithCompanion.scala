package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}

object ObjectWithCompanion {
  def unapply(td: ScTypeDefinition): Option[(ScObject, ScClass)] = td match {
    case obj: ScObject =>
      val companion = obj.baseCompanionModule.getOrElse {
        obj.syntheticNavigationElement //works for case class companion if it was injected
      }
      companion match {
        case cl: ScClass => Some((obj, cl))
        case _ => None
      }
    case _ => None
  }
}
