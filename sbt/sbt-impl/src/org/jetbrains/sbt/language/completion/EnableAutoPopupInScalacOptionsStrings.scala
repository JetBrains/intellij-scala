package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.withScalacOption

class EnableAutoPopupInScalacOptionsStrings extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
    withScalacOption(contextElement)(onMismatch = ThreeState.UNSURE, onMatch = _ => ThreeState.NO)
}

final class ScalacOptionsAutoPopupCompletionHandler extends TypedHandlerDelegate {

  import ScalacOptionsAutoPopupCompletionHandler._

  override def checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result =
    if (!file.is[ScalaFile, SbtFileImpl] || !(charTyped == '"' || charTyped == '-')) super.checkAutoPopup(charTyped, project, editor, file)
    else {
      val offset = editor.getCaretModel.getOffset
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, condition(offset)(_))
      Result.STOP
    }
}

object ScalacOptionsAutoPopupCompletionHandler {
  // "", "-" or "--"
  private def check(str: ScStringLiteral): Boolean = str.getValue.length < 3 && str.getValue.forall(_ == '-')

  private def condition(offset: Int)(file: PsiFile): Boolean = {
    val leaf = file.findElementAt(offset)
    leaf != null && withScalacOption(leaf)(onMatch = check, onMismatch = false)
  }
}
