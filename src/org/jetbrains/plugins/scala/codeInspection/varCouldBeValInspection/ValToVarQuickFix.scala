package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createVarFromValDeclaration

class ValToVarQuickFix(valDef: ScValue) extends IntentionAction {
  override def startInWriteAction: Boolean = true

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    valDef.replace(createVarFromValDeclaration(valDef)(valDef.getManager))
  }

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isInstanceOf[ScalaFile]

  def getFamilyName: String = getText

  def getText: String = "Convert 'val' to 'var'"
}
