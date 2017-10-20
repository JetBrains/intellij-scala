package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class AddLToLongLiteralFix(literal: ScLiteral) extends IntentionAction {

  override val getText: String = "add L to Long number"

  override def getFamilyName: String = "Change ScLiteral"

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    literal.isValid && literal.getManager.isInProject(file)

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!literal.isValid) return
    literal.replace(createExpressionFromText(literal.getText + "L")(literal.getManager))
  }
}

