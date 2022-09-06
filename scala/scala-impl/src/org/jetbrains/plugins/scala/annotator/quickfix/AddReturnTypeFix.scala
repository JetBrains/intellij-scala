package org.jetbrains.plugins.scala
package annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType

class AddReturnTypeFix(fun: ScFunctionDefinition, tp: ScType) extends IntentionAction {
  override def getText: String = ScalaBundle.message("add.return.type")

  override def getFamilyName: String = getText

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = doAddReturn(fun, editor)

  override def startInWriteAction(): Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = fun.returnTypeElement.isEmpty

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    doAddReturn(PsiTreeUtil.findSameElementInCopy(fun, file), editor)
    IntentionPreviewInfo.DIFF
  }

  private def doAddReturn(fn: ScFunctionDefinition, editor: Editor): Unit =
    new AddOnlyStrategy(Option(editor)).addTypeAnnotation(tp, fn.getParent, fn.parameterList)
}
