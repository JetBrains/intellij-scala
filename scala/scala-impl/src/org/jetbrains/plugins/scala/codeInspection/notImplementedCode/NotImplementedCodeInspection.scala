package org.jetbrains.plugins.scala.codeInspection.notImplementedCode

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.{TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._

class NotImplementedCodeInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case reference @ ReferenceTarget(Member("???", "scala.Predef")) =>
      holder.registerProblem(reference, ScalaInspectionBundle.message("not.implemented"), new ImplementQuickFix(reference))
    case _ =>
  }

  private class ImplementQuickFix(e: PsiElement) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("implement.quickfix.name"), e) {

    override protected def doApplyFix(elem: PsiElement)
                                     (implicit project: Project): Unit = {
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
