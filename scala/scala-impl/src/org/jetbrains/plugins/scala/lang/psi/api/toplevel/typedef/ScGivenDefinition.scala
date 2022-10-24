package org.jetbrains.plugins.scala.lang.psi.api.toplevel
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
