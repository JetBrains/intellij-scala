//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector.{CLASS_LOCATION, METHOD_LOCATION, logCodeAuthorClicked}
import com.intellij.codeInsight.hints.VcsCodeVisionLanguageContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

import java.awt.event.MouseEvent

private final class ScalaVcsCodeVisionContext extends VcsCodeVisionLanguageContext {

  import ScalaVcsCodeVisionContext._

  override def isAccepted(element: PsiElement): Boolean =
    (isAcceptedTemplateDefinition(element) || isAcceptedMember(element)) &&
      !isInWorksheetFile(element) //SCL-21098

  override def handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement): Unit = {
    val project = element.getProject
    val location = if (isAcceptedTemplateDefinition(element)) CLASS_LOCATION else METHOD_LOCATION

    logCodeAuthorClicked(project, location)
  }
}

private object ScalaVcsCodeVisionContext {
  private def isInWorksheetFile(element: PsiElement): Boolean =
    element.containingScalaFile.exists(_.isWorksheetFile)

  private def isAcceptedTemplateDefinition(element: PsiElement): Boolean =
    element.is[ScClass, ScObject, ScTrait, ScEnum, ScGivenDefinition]

  private def isAcceptedMember(element: PsiElement): Boolean = element match {
    case fn: ScFunction => isAcceptedMember(fn)
    case ext: ScExtension => isAcceptedMember(ext)
    case _ => false
  }

  private def isAcceptedMember(member: ScMember): Boolean =
    member.isTopLevel || isAcceptedTemplateDefinition(member.containingClass)
}
