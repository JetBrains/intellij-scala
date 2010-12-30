package org.jetbrains.plugins.scala
package lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.editor.{LogicalPosition, Editor}
import com.intellij.codeInsight.editorActions.moveUpDown.{LineRange, LineMover}
import com.intellij.psi.{PsiWhiteSpace, PsiElement, PsiFile}
import com.intellij.openapi.util.Pair
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import lang.psi.api.toplevel.typedef.ScMember

/**
 * Pavel Fatin
 */

class ScalaStatementMover extends LineMover {
  override def checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean = {
    if(!super.checkAvailable(editor, file, info, down)) return false
    if(!file.isInstanceOf[ScalaFile]) return false

    var result = false

    findMemberAt(editor, file, info.toMove).foreach { source =>
      val siblings = if(down) source.nextSiblings else source.prevSiblings
      siblings.findByType(classOf[ScMember]).foreach { target =>
        info.toMove = lineRangeOf(editor, source)
        info.toMove2 = lineRangeOf(editor, target)
        result = true
      }
    }

    result
  }

  private def lineRangeOf(editor: Editor, e: PsiElement) = {
    val memberRange = e.getTextRange
    new LineRange(editor.offsetToLogicalPosition(memberRange.getStartOffset).line,
      editor.offsetToLogicalPosition(memberRange.getEndOffset).line + 1)
  }

  private def findMemberAt(editor: Editor, file: PsiFile, range: LineRange): Option[ScMember] = {
    val psiRange = cornerElements(editor, file, range)

    if (psiRange == null) return None

    val firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst, classOf[ScMember], false)
    val lastMember= PsiTreeUtil.getParentOfType(psiRange.getSecond, classOf[ScMember], false)

    if (firstMember == null || lastMember == null) return None
    if(firstMember != lastMember) return None

    Some(firstMember)
  }

  protected def cornerElements(editor: Editor, file: PsiFile, range: LineRange): Pair[PsiElement, PsiElement] = {
    val startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0))
    var startingElement = firstNonWhiteElement(startOffset, file, true)
    if (startingElement == null) return null
    val endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0)) - 1
    var endingElement = firstNonWhiteElement(endOffset, file, false)
    if (endingElement == null) return null
    if (PsiTreeUtil.isAncestor(startingElement, endingElement, false) || startingElement.getTextRange.getEndOffset <= endingElement.getTextRange.getStartOffset) {
      return Pair.create(startingElement, endingElement)
    }
    if (PsiTreeUtil.isAncestor(endingElement, startingElement, false)) {
      return Pair.create(startingElement, endingElement)
    }
    return null
  }

  protected def firstNonWhiteElement(offset: Int, file: PsiFile, lookRight: Boolean): PsiElement = {
    val leafElement = file.getNode.findLeafElementAt(offset)
    return if (leafElement == null) null else firstNonWhiteElement(leafElement.getPsi, lookRight)
  }

  protected def firstNonWhiteElement(element: PsiElement, lookRight: Boolean): PsiElement = {
    if (element.isInstanceOf[PsiWhiteSpace] || element.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR) {
      return if (lookRight) element.getNextSibling else element.getPrevSibling
    }
    return element
  }
}