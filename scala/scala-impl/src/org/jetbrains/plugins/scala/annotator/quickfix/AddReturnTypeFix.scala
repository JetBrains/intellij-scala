package org.jetbrains.plugins.scala
package annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType

class AddReturnTypeFix(fun: ScFunctionDefinition, tp: ScType) extends IntentionAction {
  override def getText: String = ScalaBundle.message("add.return.type")

  override def getFamilyName: String = getText

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    new AddOnlyStrategy(Option(editor)).addTypeAnnotation(tp, fun.getParent, fun.parameterList)
  }

  override def startInWriteAction(): Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = fun.returnTypeElement.isEmpty
}
