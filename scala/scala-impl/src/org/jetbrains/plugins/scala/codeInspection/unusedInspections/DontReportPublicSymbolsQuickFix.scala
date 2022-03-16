package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

final class DontReportPublicSymbolsQuickFix(named: ScNamedElement)
  extends LocalQuickFixAndIntentionActionOnPsiElement(named) {
  override def getText: String = ScalaInspectionBundle.message("fix.unused.symbol.report.public.symbols")

  override def getFamilyName: String = getText

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit =
    ScalaUnusedLocalSymbolPass.inspection(project).setReportPublicSymbols(false)
}
