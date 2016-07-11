package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool
import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFixAndIntentionActionOnPsiElement, ProblemsHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

// This is checked in ScalaUnusedLocalSymbolPass, the inspection is to allow this to be
// turned on/off in the Inspections settings.
class ScalaUnusedSymbolInspection extends LocalInspectionTool with UnfairLocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    PsiElementVisitor.EMPTY_VISITOR
  }
}

object ScalaUnusedSymbolInspection {
  val Annotation = "Declaration is never used"

  val ShortName: String = "ScalaUnusedSymbol"
}

class DeleteUnusedElementFix(e: ScNamedElement) extends LocalQuickFixAndIntentionActionOnPsiElement(e) {
  override def getText: String = DeleteUnusedElementFix.Hint

  override def getFamilyName: String = getText

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (FileModificationService.getInstance.prepareFileForWrite(startElement.getContainingFile)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      startElement.delete()
    }
  }
}

object DeleteUnusedElementFix {
  val Hint: String = "Remove unused element"
}