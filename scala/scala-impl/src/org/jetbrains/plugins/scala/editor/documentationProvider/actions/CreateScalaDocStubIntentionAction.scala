package org.jetbrains.plugins.scala.editor.documentationProvider.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.extensions.{&, ElementType, Parent}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner

final class CreateScalaDocStubIntentionAction extends IntentionAction {

  override def getText: String = ScalaEditorBundle.message("add.scaladoc.intention.action.text")

  override def getFamilyName: String = ScalaEditorBundle.message("add.scaladoc.intention.action.family.name")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!file.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      return false

    findDocOwner(editor, file) match {
      case Some(value) =>
        value.docComment.isEmpty
      case None =>
        false
    }
  }

  private def findDocOwner(editor: Editor, file: PsiFile): Option[ScDocCommentOwner] = {
    val caretOffset = editor.getCaretModel.getOffset
    val element = file.findElementAt(caretOffset)

    element match {
      case ElementType(ScalaTokenTypes.tIDENTIFIER) & Parent(docOwner: ScDocCommentOwner) =>
        Some(docOwner)
      case _ =>
        None
    }
  }

  // If false is returned the action itself is responsible for starting write action
  override def startInWriteAction(): Boolean = false

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val docOwner = findDocOwner(editor, file)
    docOwner.foreach(CreateScalaDocStubAction.createStub(_, editor.getDocument))
  }
}
