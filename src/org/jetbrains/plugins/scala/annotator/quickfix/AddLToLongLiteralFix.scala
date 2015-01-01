package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class AddLToLongLiteralFix(literal: ScLiteral) extends IntentionAction {
  val getText: String = "add L to Long number"

  def getFamilyName: String = "Change ScLiteral"

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = literal.isValid && literal.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!literal.isValid) return
    val psi = ScalaPsiElementFactory.createExpressionFromText(literal.getText + "L", literal.getManager)
    literal.replace(psi)
  }
}

