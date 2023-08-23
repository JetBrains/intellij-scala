package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

object ScalaEnumeration {
  private[this] val EnumerationFQN = "scala.Enumeration"

  def unapply(enumClass: ScObject): Option[Seq[ScValue]] = {
    val isEnumeration = enumClass.supers.map(_.qualifiedName).contains(EnumerationFQN)
    Option.when(isEnumeration)(enumClass.members.filterByType[ScValue])
  }
}
