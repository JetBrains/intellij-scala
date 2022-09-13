package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createVarFromValDeclaration

final class ValToVarQuickFix(value: ScValue) extends IntentionAction {

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    file.is[ScalaFile]

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val replacement = createVarFromValDeclaration(value)
    value.replace(replacement)
  }

  override def getText: String = ScalaInspectionBundle.message("convert.val.to.var")

  override def getFamilyName: String = getText

  override def startInWriteAction: Boolean = true

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new ValToVarQuickFix(PsiTreeUtil.findSameElementInCopy(value, target))
}
