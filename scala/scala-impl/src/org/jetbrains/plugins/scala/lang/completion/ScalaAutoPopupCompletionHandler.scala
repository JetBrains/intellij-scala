package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInsight.intention.types.AbstractTypeAnnotationIntention
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaAutoPopupCompletionHandler._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

final class ScalaAutoPopupCompletionHandler extends TypedHandlerDelegate {
  override def charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result =
    if (!file.is[ScalaFile] || char != ':') super.charTyped(char, project, editor, file)
    else {
      val offset = editor.getCaretModel.getOffset - 1

      val leaf = file.findElementAt(offset)
      val needsAutoPopup = leaf != null &&
        functionParentWithoutType(leaf)
          .orElse(valueParentWithoutType(leaf))
          .orElse(variableParentWithoutType(leaf))
          .isDefined

      if (needsAutoPopup) {
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null)
        Result.STOP
      } else Result.CONTINUE
    }
}

object ScalaAutoPopupCompletionHandler {
  private def functionParentWithoutType(element: PsiElement): Option[ScFunctionDefinition] =
    AbstractTypeAnnotationIntention.functionParent(element).filter(_.returnTypeElement.isEmpty)

  private def valueParentWithoutType(element: PsiElement): Option[ScPatternDefinition] =
    AbstractTypeAnnotationIntention.valueParent(element).filter(_.typeElement.isEmpty)

  private def variableParentWithoutType(element: PsiElement): Option[ScVariableDefinition] =
    AbstractTypeAnnotationIntention.variableParent(element).filter(_.typeElement.isEmpty)
}
