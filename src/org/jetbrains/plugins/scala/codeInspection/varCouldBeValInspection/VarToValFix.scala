package org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createValFromVarDefinition

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class VarToValFix(elem: ScVariableDefinition) extends LocalQuickFixAndIntentionActionOnPsiElement(elem) {
  override def getText: String = VarToValFix.Hint

  override def getFamilyName: String = getText

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    startElement match {
      case varDef: ScVariableDefinition =>
        if (FileModificationService.getInstance.prepareFileForWrite(varDef.getContainingFile)) {
          varDef.replace(createValFromVarDefinition(varDef)(varDef.getManager))
        }
      case _ =>
    }
  }
}

object VarToValFix {
  val Hint: String = "Convert var to val"
}
