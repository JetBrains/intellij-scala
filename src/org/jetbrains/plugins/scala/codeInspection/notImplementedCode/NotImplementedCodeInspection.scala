package org.jetbrains.plugins.scala
package codeInspection
package notImplementedCode

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.{TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel Fatin
 */

class NotImplementedCodeInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case reference @ ReferenceTarget(Member("???", "scala.Predef")) =>
      holder.registerProblem(reference, "Not implemented",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ImplementQuickFix(reference))
  }

  private class ImplementQuickFix(e: PsiElement) extends AbstractFixOnPsiElement("Implement", e) {
    def doApplyFix(project: Project) {
      val elem = getElement
      val builder = new TemplateBuilderImpl(elem)
      builder.replaceElement(elem, elem.getText)
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(elem)
      val template = builder.buildTemplate()
      val editor = positionCursor(project, elem.getContainingFile, elem)
      val range = elem.getTextRange
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
