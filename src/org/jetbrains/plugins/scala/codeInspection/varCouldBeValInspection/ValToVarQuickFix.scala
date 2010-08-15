package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.openapi.project.Project

import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import com.intellij.psi.codeStyle.CodeStyleManager

class ValToVarQuickFix(varDef: ScVariableDefinition) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    val parent = varDef.getContext
    varDef.replace(ScalaPsiElementFactory.createValFromVarDeclaration(varDef, varDef.getManager))

    CodeStyleManager.getInstance(varDef.getProject()).reformatText(varDef.getContainingFile,
      parent.getTextRange.getStartOffset,
      parent.getTextRange.getEndOffset)
  }

  def getName: String = "Convert 'var' to 'val'"

  def getFamilyName: String = getName
}