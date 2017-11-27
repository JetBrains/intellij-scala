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

class ValToVarQuickFix(value: ScValue) extends IntentionAction {

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    file.isInstanceOf[ScalaFile]

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val replacement = createVarFromValDeclaration(value)
    value.replace(replacement)
  }

  override def getText: String = VarCouldBeValInspection.DESCRIPTION

  override def getFamilyName: String = getText

  override def startInWriteAction: Boolean = true
}

object ValToVarQuickFix {
  val DESCRIPTION: String = "Convert 'val' to 'var'"
}
