package org.jetbrains.plugins.scala.codeInspection.notImplementedCode

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil

final class NotImplementedMembersAction extends BaseElementAtCaretIntentionAction {
  override def getText: String = ScalaBundle.message("implement.members.fix")

  override def getFamilyName: String = getText

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project) &&
      (element.getContext match {
        case clazz: ScTemplateDefinition =>
          ScalaOIUtil.getMembersToImplement(clazz, withOwn = true).nonEmpty
        case _ => false
      })

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    element.getContext match {
      case clazz: ScTemplateDefinition =>
        ScalaOIUtil.invokeOverrideImplement(clazz, isImplement = true)(project, editor)
      case _ =>
  }

  override def startInWriteAction(): Boolean = false
}
