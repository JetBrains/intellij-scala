package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class VarToValQuickFix(varDef: ScVariableDefinition)
        extends AbstractFixOnPsiElement(ScalaBundle.message("convert.var.to.val"), varDef) {
  def doApplyFix(project: Project): Unit = {
    val varDef = getElement
    varDef.replace(ScalaPsiElementFactory.createValFromVarDefinition(varDef, varDef.getManager))
  }
}

class ValToVarQuickFix(valDef: ScValue) extends IntentionAction {
  def startInWriteAction: Boolean = true

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val parent = valDef.getContext
    valDef.replace(ScalaPsiElementFactory.createVarFromValDeclaration(valDef, valDef.getManager))
  }

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isInstanceOf[ScalaFile]

  def getFamilyName: String = getText

  def getText: String = "Convert 'val' to 'var'"
}