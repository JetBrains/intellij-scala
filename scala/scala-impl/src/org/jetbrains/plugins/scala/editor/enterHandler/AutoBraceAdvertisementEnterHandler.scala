package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.AutoBraceAdvertiser
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr

class AutoBraceAdvertisementEnterHandler extends EnterHandlerDelegateAdapter {

  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    if (!AutoBraceAdvertiser.shouldAdvertiseAutoBraces) {
      return Result.Continue
    }

    val elem = file.findElementAt(caretOffsetRef.get())

    def bracesAreOnSameLine(block: ScBlockExpr): Boolean = {
      val document = editor.getDocument
      val range = block.getTextRange
      document.getLineNumber(range.getStartOffset) == document.getLineNumber(range.getEndOffset)
    }

    elem.toOption.flatMap(_.prevVisibleLeaf(skipComments = true)).foreach {
      case elem@Parent(block: ScBlockExpr) =>
        if (elem.elementType == ScalaTokenTypes.tLBRACE && bracesAreOnSameLine(block)) {
          AutoBraceAdvertiser.advertiseAutoBraces(file.getProject)
        }

      case _ =>
    }

    Result.Continue
  }
}
