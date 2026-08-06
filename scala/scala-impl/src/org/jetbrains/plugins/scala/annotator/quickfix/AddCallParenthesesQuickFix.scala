package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class AddCallParenthesesQuickFix(reference: PsiElement) extends LocalQuickFixAndIntentionActionOnPsiElement(reference) {
  override def invoke(project: Project, psiFile: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!reference.isValid) return

    val replacement = ScalaPsiElementFactory.createExpressionFromText(s"${reference.getText}()", reference)(project)

    IntentionPreviewUtils.write { () =>
      reference.replace(replacement)
    }
  }

  override def getText: String = ScalaInspectionBundle.message("add.call.parentheses")
  override def getFamilyName: String = getText
}
