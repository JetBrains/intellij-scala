package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createVarFromValDeclaration

class ValToVarQuickFix(value: ScValue) extends IntentionAction {

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    file.is[ScalaFile]

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    replace(value)

  override def getText: String = ScalaInspectionBundle.message("convert.val.to.var")

  override def getFamilyName: String = getText

  override def startInWriteAction: Boolean = true

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    replace(PsiTreeUtil.findSameElementInCopy(value, file))
    IntentionPreviewInfo.DIFF
  }

  private def replace(v: ScValue): Unit = {
    val replacement = createVarFromValDeclaration(v)
    v.replace(replacement)
  }
}
