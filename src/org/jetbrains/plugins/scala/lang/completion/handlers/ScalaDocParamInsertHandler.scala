package org.jetbrains.plugins.scala
package lang.completion.handlers

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.openapi.editor.Editor
import lang.psi.api.ScalaFile
import lang.scaladoc.psi.api.ScDocTag
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}

/**
 * User: Dmitry Naydanov
 * Date: 2/1/12
 */

class ScalaDocParamInsertHandler extends EnterHandlerDelegateAdapter {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!file.isInstanceOf[ScalaFile]) {
      return Result.Continue
    }

    val scalaFile = file.asInstanceOf[ScalaFile]
    val caretOffset = editor.getCaretModel.getOffset
    scalaFile.elementAt(caretOffset) match {
      case None => return Result.Continue
      case _ =>
    }

    var nextParent = scalaFile.elementAt(caretOffset).get
    val document = editor.getDocument

    while (!nextParent.isInstanceOf[ScDocTag]) {
      nextParent = nextParent.getParent
      if (nextParent == scalaFile) {
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

    val startOffset = tagParent.getNameElement.getTextRange.getStartOffset
    val endOffset = probData.getTextRange.getStartOffset + (if (probData.getText.trim().length() != 0)
      probData.getText.indexWhere(_ != ' ') else 1)

    if (document.getLineNumber(caretOffset) - 1 == document.getLineNumber(tagParent.getNameElement.getTextOffset)) {
      val toInsert = StringUtil.repeat(" ", endOffset - startOffset)
      ApplicationManager.getApplication.runWriteAction(new Runnable {
        def run() {
          document.insertString(caretOffset, toInsert)
          PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
        }
      })
    }


    Result.Continue
  }
}