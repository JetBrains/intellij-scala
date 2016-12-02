package org.jetbrains.plugins.scala
package lang.completion.handlers

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * User: Dmitry Naydanov
 * Date: 2/1/12
 */

class ScalaDocParamEnterHandlerDelegate extends EnterHandlerDelegateAdapter {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!file.isInstanceOf[ScalaFile]) {
      return Result.Continue
    }
    val document = editor.getDocument
    PsiDocumentManager.getInstance(file.getProject).commitDocument(document)

    val scalaFile = file.asInstanceOf[ScalaFile]
    val caretOffset = editor.getCaretModel.getOffset

    var nextParent = scalaFile.findElementAt(caretOffset)
    if (nextParent == null) {
      return Result.Continue
    }

    while (!nextParent.isInstanceOf[ScDocTag]) {
      nextParent = nextParent.getParent
      if (nextParent == null || nextParent.isInstanceOf[ScalaFile]) {
        return Result.Continue
      }
    }
    val tagParent = nextParent.asInstanceOf[ScDocTag]
    val tagValueElement = tagParent.getValueElement
    val tagNameElement = tagParent.getNameElement

    if (tagParent.getNameElement == null) {
      return Result.Continue
    }

    val probData = if (tagValueElement != null) tagValueElement.getNextSibling else tagNameElement.getNextSibling
    if (probData == null) {
      return Result.Continue
    }
    val nextProbData = if (probData.getNextSibling != null) probData.getNextSibling.getNode else null

    val startOffset = tagParent.getNameElement.getTextRange.getStartOffset
    val endOffset = probData.getTextRange.getStartOffset + (Option(nextProbData).map(_.getElementType) match {
      case Some(ScalaDocTokenType.DOC_COMMENT_DATA) => probData.getTextLength
      case Some(ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) => 1
      case _ => 0
    })
    
    if (document.getLineNumber(caretOffset) - 1 == document.getLineNumber(tagParent.getNameElement.getTextOffset)) {
      val toInsert = StringUtil.repeat(" ", endOffset - startOffset)
      extensions.inWriteAction {
        document.insertString(caretOffset, toInsert)
        PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
      }
    }


    Result.Continue
  }
}