package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler

final class CreateParameterQuickFix(ref: ScReferenceExpression) extends CreateFromUsageQuickFixBase(ref) {
  override def getText: String = ScalaBundle.message("create.parameter.named", ref.nameId.getText)

  override def getFamilyName: String = ScalaBundle.message("family.name.create.parameter")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    super.isAvailable(project, editor, file) &&
      ref.qualifier.isEmpty &&
      ref.parentOfType(Seq(classOf[ScFunctionDefinition], classOf[ScClass])).isDefined

  override protected def invokeInner(project: Project, editor: Editor, file: PsiFile): Unit = {
    val handler = new ScalaIntroduceParameterHandler
    editor.getSelectionModel.setSelection(ref.startOffset, ref.endOffset)

    val dataContext = editor match {
      case e: EditorEx => e.getDataContext
      case _ => DataContext.EMPTY_CONTEXT
    }

    handler.invoke(file)(project, editor, dataContext)
  }

  // TODO: Add preview (SCL-20398)
  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
    IntentionPreviewInfo.EMPTY
}
