package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

trait ScGivenDefinition extends ScTemplateDefinition with ScGiven {
  def desugaredDefinitions: Seq[ScMember]
}

object ScGivenDefinition {
  object DesugaredTypeDefinition {
    def unapply(tdef: ScTypeDefinition): Option[ScGivenDefinition] =
      Option(tdef.originalGivenElement)
  }
}
