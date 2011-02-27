package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}

class RemoveValQuickFix(param: ScClassParameter) extends LocalQuickFix{
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    param.findChildrenByType(ScalaTokenTypes.kVAL).foreach(_.delete())
    CodeStyleManager.getInstance(param.getProject()).reformatText(param.getContainingFile,
      param.getModifierList.getTextRange.getStartOffset,
      param.getModifierList.getTextRange.getEndOffset)
  }

  def getName: String = "Remove 'val'"

  def getFamilyName: String = "Remove 'val'"
}