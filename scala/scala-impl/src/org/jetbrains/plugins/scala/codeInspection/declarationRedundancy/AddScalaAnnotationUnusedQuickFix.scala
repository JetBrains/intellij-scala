package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * QuickFix that adds @scala.annotation.unused to unused elements.
 *
 * This annotation was introduced in Scala 2.13, so this QuickFix should
 * only be offered when the declaration is part of a Scala 2.13 or higher
 * project.
 */
private final class AddScalaAnnotationUnusedQuickFix(named: ScNamedElement)
  extends LocalQuickFixAndIntentionActionOnPsiElement(named) {
  
  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit =
    named.nameContext match {
      case p: ScParameter =>
        p.addAnnotation("scala.annotation.unused", addNewLine = false)
      case a: ScAnnotationsHolder =>
        a.addAnnotation("scala.annotation.unused")
      case _ => ()
    }

  override def getText: String = ScalaInspectionBundle.message("annotate.declaration.with.unused")

  override def getFamilyName: String = getText

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new AddScalaAnnotationUnusedQuickFix(PsiTreeUtil.findSameElementInCopy(named, target))
}
