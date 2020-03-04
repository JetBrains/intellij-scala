package org.jetbrains.plugins.scala.editor
package enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * @see [[com.intellij.codeInsight.editorActions.enter.EnterInBlockCommentHandler]]
 *      [[com.intellij.codeInsight.editorActions.enter.EnterInLineCommentHandler]]
 *      [[org.jetbrains.plugins.scala.lang.scaladoc.ScalaIsCommentComplete]]
 *      [[org.jetbrains.plugins.scala.highlighter.ScalaCommenter]]
 */
class ScalaDocParamEnterHandlerDelegate extends EnterHandlerDelegateAdapter {

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!file.isInstanceOf[ScalaFile] || !editor.inDocComment(editor.offset))
      return Result.Continue

    val document = editor.getDocument
    val project = file.getProject
    document.commit(project)

    val scalaFile = file.asInstanceOf[ScalaFile]
    val caretOffset = editor.getCaretModel.getOffset

    val elementAtCaret = scalaFile.findElementAt(caretOffset)
    if (elementAtCaret == null)
      return Result.Continue

    var nextParent = elementAtCaret
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
      }
    }


    Result.Continue
  }
}