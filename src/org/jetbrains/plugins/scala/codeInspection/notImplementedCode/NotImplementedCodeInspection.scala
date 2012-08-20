package org.jetbrains.plugins.scala
package codeInspection
package notImplementedCode

import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInsight.template.{TemplateManager, TemplateBuilderImpl}
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import extensions._

/**
 * Pavel Fatin
 */

class NotImplementedCodeInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case reference @ ReferenceTarget(Member("???", "scala.Predef")) =>
      holder.registerProblem(reference, "Not implemented",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ImplementQuickFix(reference))
  }

  private class ImplementQuickFix(e: PsiElement) extends AbstractFix("Implement", e) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      val builder = new TemplateBuilderImpl(e)
      builder.replaceElement(e, e.getText)
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(e)
      val template = builder.buildTemplate()
      val editor = positionCursor(project, e.getContainingFile, e)
      val range = e.getTextRange
      editor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
      TemplateManager.getInstance(project).startTemplate(editor, template)
    }
  }

  private def positionCursor(project: Project, targetFile: PsiFile, element: PsiElement): Editor = {
    val range = element.getTextRange
    val textOffset = range.getStartOffset
    val descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile, textOffset)
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
  }
}
