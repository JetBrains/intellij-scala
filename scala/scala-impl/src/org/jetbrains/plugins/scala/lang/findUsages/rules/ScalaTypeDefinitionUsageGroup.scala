package org.jetbrains.plugins.scala.lang.findUsages.rules

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

private class ScalaTypeDefinitionUsageGroup(td: ScTypeDefinition) extends ScalaDeclarationUsageGroupBase(td, td.name) {
  private val myText: String = namePathFromFileRoot(td)

  override def getPresentableGroupText: String = myText

  /** @return name of class in a fully-qualified style, but only with the names of all containing classes, without the package */
  private def namePathFromFileRoot(td: ScTemplateDefinition) = {
    var text = td.name
    var containingClass: PsiClass = td.containingClass
    while (containingClass != null) {
      text = containingClass.name + '.' + text
      containingClass = containingClass.containingClass
    }
    text
  }
}
