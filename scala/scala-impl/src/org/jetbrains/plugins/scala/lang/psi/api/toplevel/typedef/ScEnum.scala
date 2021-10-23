package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase

trait ScEnum extends ScConstructorOwner with ScDerivesClauseOwner {
  def cases: Seq[ScEnumCase]

  def syntheticClass: Option[ScTypeDefinition]
}

object ScEnum {
  object Original {
    def unapply(cls: ScClass): Option[ScEnum] =
      Option(cls.originalEnumElement)
  }

  def isDesugaredEnumClass(cls: ScTypeDefinition): Boolean =
    cls.originalEnumElement ne null
}
