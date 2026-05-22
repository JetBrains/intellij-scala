package org.jetbrains.plugins.scala.editor.documentationProvider.actions

import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInsight.intention.{FileModifier, PriorityAction, PsiElementBaseIntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.extensions.{&, ElementType, Parent}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner

final class CreateScalaDocStubIntentionAction
  extends PsiElementBaseIntentionAction
    with PriorityAction
    with DumbAware {

  override def getText: String = ScalaEditorBundle.message("add.scaladoc.intention.action.text")

  override def getFamilyName: String = ScalaEditorBundle.message("add.scaladoc.intention.action.family.name")

  //Setting priority to LOW primarily to move it below "Add type annotation action"
  //Because the latter seems to be a more frequent action
  override def getPriority: PriorityAction.Priority = Priority.LOW

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findDocOwner(element) match {
      case Some((location, _)) =>
        location.docComment.isEmpty
      case None =>
        false
    }

  private def findDocOwner(element: PsiElement): Option[(ScDocCommentOwner, ScDocCommentOwner)] =
    element match {
      case ElementType(ScalaTokenTypes.tIDENTIFIER) & Parent(docOwner: ScDocCommentOwner) =>
        docOwner match {
          case (_: ScEnumCase) & Parent(cses: ScEnumCases) =>
            if (cses.declaredElements.length > 1) None else Some((cses, docOwner))
          case _ => Some((docOwner, docOwner))
        }
      case _ =>
        None
    }

  // If false is returned, the action itself is responsible for starting write action
  override def startInWriteAction(): Boolean = false

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val docOwner = findDocOwner(element)
    docOwner.foreach { case (loc, owner) =>
      CreateScalaDocStubAction.createStub(loc, owner, editor.getDocument)
    }
  }

  override def checkFile(file: PsiFile): Boolean =
    super.checkFile(file) && file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new CreateScalaDocStubIntentionAction

  override def getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile
}
