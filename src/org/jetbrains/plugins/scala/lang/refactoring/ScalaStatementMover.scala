package org.jetbrains.plugins.scala
package lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.editorActions.moveUpDown.{LineRange, LineMover}
import com.intellij.psi.{PsiWhiteSpace, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import lang.psi.api.toplevel.typedef.ScMember
import lang.psi.ScalaPsiElement
import lang.psi.api.base.patterns.ScCaseClause
import com.intellij.lang.ASTNode

/**
 * Pavel Fatin
 */

class ScalaStatementMover extends LineMover {
  override def checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean = {
    if(!super.checkAvailable(editor, file, info, down)) return false
    if(editor.getSelectionModel.hasSelection) return false
    if(!file.isInstanceOf[ScalaFile]) return false

    def aim[T <: ScalaPsiElement](cl: Class[T]): Option[(LineRange, LineRange)] = {
      findElementAt(cl, editor, file, info.toMove).flatMap { source =>
        val siblings = if(down) source.nextSiblings else source.prevSiblings
        val r = siblings.findByType(cl).map(target => (lineRangeOf(editor, source), lineRangeOf(editor, target)))
        r
      }
    }

    val destination = aim(classOf[ScCaseClause]).orElse(aim(classOf[ScMember]))

    destination.foreach { it =>
      info.toMove = it._1
      info.toMove2 = it._2
    }

    destination.isDefined
  }

  private def lineRangeOf(editor: Editor, e: PsiElement) = {
    val memberRange = e.getTextRange
    new LineRange(editor.offsetToLogicalPosition(memberRange.getStartOffset).line,
      editor.offsetToLogicalPosition(memberRange.getEndOffset).line + 1)
  }

  private def findElementAt[T <: ScalaPsiElement](cl: Class[T], editor: Editor, file: PsiFile, range: LineRange): Option[T] = {
    val psiRange = edgeLeafsOf(range.startLine, editor, file)

    if (psiRange == null) return None

    val first = PsiTreeUtil.getParentOfType(psiRange._1, cl, false)
    val last = PsiTreeUtil.getParentOfType(psiRange._2, cl, false)

    if (first == null || last == null) return None

    if(first != last) return None

    if(editor.offsetToLogicalPosition(first.getTextOffset).line != range.startLine) return None

    Some(first)
  }

  private def edgeLeafsOf(line: Int, editor: Editor, file: PsiFile): (PsiElement, PsiElement) = {
    val document = editor.getDocument

    val start = document.getLineStartOffset(line)
    val end = start.max(document.getLineEndOffset(line) - 1)

    val span = start to end

    def isWhitespace(node: ASTNode) = node.getPsi.isInstanceOf[PsiWhiteSpace]

    def firstLeafOf(seq: Seq[Int]) = seq.view.map(file.getNode.findLeafElementAt(_))
            .filter(!isWhitespace(_)).map(_.getPsi).headOption

    val left = firstLeafOf(span)
    val right = firstLeafOf(span.reverse)

    (left.orNull, right.orNull)
  }
}