package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import com.intellij.psi.PsiElement

trait ScGivenDefinition extends ScTemplateDefinition with ScGiven {
  def desugaredDefinitions: Seq[ScMember]

  override def getNavigationElement: PsiElement =
    if (nameElement.isDefined) super.getNavigationElement else extendsBlock
}

object ScGivenDefinition {
  object DesugaredTypeDefinition {
    def unapply(tdef: ScTypeDefinition): Option[ScGivenDefinition] =
      Option(tdef.originalGivenElement)
  }
}
