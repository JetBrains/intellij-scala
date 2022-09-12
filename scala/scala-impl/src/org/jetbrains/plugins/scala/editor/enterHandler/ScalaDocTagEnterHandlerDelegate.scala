package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.editor.{DocumentExt, EditorExt}
import org.jetbrains.plugins.scala.extensions.{ElementType, PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * @see [[com.intellij.codeInsight.editorActions.enter.EnterInBlockCommentHandler]]
 *      [[com.intellij.codeInsight.editorActions.enter.EnterInLineCommentHandler]]
 *      [[org.jetbrains.plugins.scala.lang.scaladoc.ScalaIsCommentComplete]]
 *      [[org.jetbrains.plugins.scala.highlighter.ScalaCommenter]]
 */
class ScalaDocTagEnterHandlerDelegate extends EnterHandlerDelegateAdapter {

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

    val tagParent = elementAtCaret.nonStrictParentOfType(classOf[ScDocTag]) match {
      case Some(tag) => tag
      case None      => return Result.Continue
    }
    val tagNameElement = tagParent.getNameElement
    val tagValueElement = tagParent.getValueElement

    if (tagParent.getNameElement == null)
      return Result.Continue

    val spaceElement = PsiTreeUtil.nextLeaf(if (tagValueElement != null) tagValueElement else tagNameElement)
    if (spaceElement == null || spaceElement.elementType != ScalaDocTokenType.DOC_WHITESPACE)
      return Result.Continue

    val caretIsOnFirstLineAfterTag =
      document.getLineNumber(caretOffset) - 1 == document.getLineNumber(tagNameElement.startOffset)
    if (caretIsOnFirstLineAfterTag) {
      val spacesCount = extraIndentSize(tagNameElement, spaceElement)
      val spacesToInsert  = StringUtil.repeat(" ", spacesCount)
      inWriteAction {
        document.insertString(caretOffset, spacesToInsert)
      }
    }

    Result.Continue
  }

  private def extraIndentSize(tagNameElement: PsiElement, spaceElement: PsiElement): Int =
    PsiTreeUtil.nextLeaf(spaceElement) match {
      case null                                                         => 0
      case ElementType(ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) => 0
      case _                                                            => spaceElement.endOffset - tagNameElement.startOffset
    }
}