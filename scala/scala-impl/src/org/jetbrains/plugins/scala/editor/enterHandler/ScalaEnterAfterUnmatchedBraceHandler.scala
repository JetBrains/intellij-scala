package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pair
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.{PsiElement, PsiFile, TokenType}
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaEnterAfterUnmatchedBraceHandler extends EnterAfterUnmatchedBraceHandler {
  override def isApplicable(file: PsiFile, caretOffset: Int): Boolean =
    file.isInstanceOf[ScalaFile]

  // ATTENTION!
  // implementation of this method was copied from EnterAfterUnmatchedBraceHandler.java (version 191.6183.87)
  // list of changes:
  // 1) do not filter ignore question mark sign
  override protected def getRBraceOffset(file: PsiFile, editor: Editor, caretOffset: Int): Int = {
    val text = editor.getDocument.getCharsSequence
    var offset = CharArrayUtil.shiftForward(text, caretOffset, " \t")
    val fileLength = text.length
    val blacklistChars = ")];,%<"
    if (offset < fileLength && blacklistChars.indexOf(text.charAt(offset)) < 0) {
      offset = calculateOffsetToInsertClosingBrace(file, text, offset).second
    }
    Math.min(offset, fileLength)
  }

  // ATTENTION!
  // implementation of this method was copied from EnterAfterUnmatchedBraceHandler.java (version 191.6183.87)
  // list of changes:
  // 1) using element.getTextRange.getStartOffset instead of element.getTextOffset
  //    see getTextOffset documentation, looks like this is a typo in Intellij platform, because
  //    getTextOffset can be greater than get getTextRange.getStartOffset and we will not
  //    add closing brace in the end of such element in such case
  override protected def calculateOffsetToInsertClosingBrace(file: PsiFile, text: CharSequence, offset: Int): Pair[PsiElement, Integer] = {
    var element = PsiUtilCore.getElementAtOffset(file, offset)
    val node = element.getNode
    if (node != null && (node.getElementType eq TokenType.WHITE_SPACE))
      return Pair.create(null, CharArrayUtil.shiftForwardUntil(text, offset, "\n"))

    var parent = element.getParent
    var done = false
    while (parent != null && !done) {
      val parentNode = parent.getNode
      if (parentNode == null || parentNode.getStartOffset != offset)
        done = true
      else {
        element = parent
        parent = parent.getParent
      }
    }

    // CHANGE
    if (element.getTextRange.getStartOffset != offset) {
      Pair.create(null, CharArrayUtil.shiftForwardUntil(text, offset, "\n"))
    } else {
      Pair.create(element, calculateOffsetToInsertClosingBraceInsideElement(element))
    }
  }
}