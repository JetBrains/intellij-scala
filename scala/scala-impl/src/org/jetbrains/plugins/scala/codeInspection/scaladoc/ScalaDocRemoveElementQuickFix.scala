package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

class ScalaDocRemoveElementQuickFix(tag: ScDocTag)
  extends AbstractFixOnPsiElement(
    ScalaBundle.message("remove.generic", s"${tag.name} ${Option(tag.getValueElement).map(_.getText).getOrElse("")}".trim),
    tag
  ) {

  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(tag: ScDocTag)
                                   (implicit project: Project): Unit = {
    tag.delete()
  }
}
