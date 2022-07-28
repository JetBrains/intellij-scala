package org.jetbrains.plugins.scala
package lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo
import com.intellij.codeInsight.editorActions.moveUpDown.{LineMover, LineRange}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

class ScalaStatementMover extends LineMover {
  private type ElementClass = Class[_ <: PsiElement]

  override def checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean = {
    if(!super.checkAvailable(editor, file, info, down)) return false
    if(editor.getSelectionModel.hasSelection) return false

    if(!file.isInstanceOf[ScalaFile]) return false

    def aim(sourceClass: ElementClass, predicate: PsiElement => Boolean, canUseLineAsTarget: Boolean = true): Option[(PsiElement, LineRange)] = {
      findSourceOf(sourceClass).map { source =>
        val targetRange = findTargetRangeFor(source, predicate).getOrElse {
          if (canUseLineAsTarget) nextLineRangeFor(source) else null
        }
        (source, targetRange)
      }
    }

    def findSourceOf(aClass: ElementClass) = findElementAt(aClass, editor, file, info.toMove.startLine)

    def findTargetRangeFor(source: PsiElement, predicate: PsiElement => Boolean): Option[LineRange] = {
      val siblings = if(down) source.nextSiblings else source.prevSiblings
      siblings.filter(!_.isInstanceOf[PsiComment] )
              .takeWhile(it => it.isInstanceOf[PsiWhiteSpace] || it.isInstanceOf[PsiComment] || it.isInstanceOf[ScImportStmt] || predicate(it))
              .find(predicate)
              .map(rangeOf(_, editor))
    }

    def nextLineRangeFor(source: PsiElement): LineRange = {
      val range = rangeOf(source, editor)
      if (down) {
        val maxLine = editor.offsetToLogicalPosition(editor.getDocument.getTextLength).line
        if (range.endLine < maxLine) new LineRange(range.endLine, range.endLine + 1) else null
      } else {
        new LineRange(range.startLine - 1, range.startLine)
      }
    }

    val pair = aim(classOf[ScCaseClause], _.isInstanceOf[ScCaseClause], canUseLineAsTarget = false)
            .orElse(aim(classOf[ScMember], it => it.isInstanceOf[ScMember] || it.isInstanceOf[ScImportStmt]))
            .orElse(aim(classOf[ScIf], _ => false))
            .orElse(aim(classOf[ScFor], _ => false))
            .orElse(aim(classOf[ScMatch], _ => false))
            .orElse(aim(classOf[ScTry], _ => false))
            .orElse(aim(classOf[ScMethodCall], isControlStructureLikeCall).filter(p => isControlStructureLikeCall(p._1)))

    pair.foreach { it =>
      info.toMove = rangeOf(it._1, editor)
      info.toMove2 = it._2
    }

    pair.isDefined
  }

  private def isControlStructureLikeCall(element: PsiElement): Boolean = element match {
    case call: ScMethodCall => call.argumentExpressions.lastOption.exists(_.isInstanceOf[ScBlockExpr])
    case _ => false
  }

  private def rangeOf(e: PsiElement, editor: Editor) = {
    val begin = editor.offsetToLogicalPosition(e.getTextRange.getStartOffset).line
    val end = editor.offsetToLogicalPosition(e.getTextRange.getEndOffset).line + 1
    new LineRange(begin, end)
  }

  private def findElementAt(cl: ElementClass, editor: Editor, file: PsiFile, line: Int): Option[PsiElement] = {
    val edges = edgeLeafsOf(line, editor, file)

    val left = edges._1.flatMap(PsiTreeUtil.getParentOfType(_, cl, false).toOption)
    val right = edges._2.flatMap(PsiTreeUtil.getParentOfType(_, cl, false).toOption)

    left.zip(right)
      .collect { case (l: PsiElement, r: PsiElement) if l.withParentsInFile.contains(r) => r }
      .find(it => editor.offsetToLogicalPosition(it.getTextOffset).line == line)
  }

  private def edgeLeafsOf(line: Int, editor: Editor, file: PsiFile): (Option[PsiElement], Option[PsiElement]) = {
    val document = editor.getDocument

    val start = document.getLineStartOffset(line)
    val end = start.max(document.getLineEndOffset(line) - 1)

    val span = start.to(end)

    def firstLeafOf(seq: Seq[Int]) = seq.view.flatMap(file.getNode.findLeafElementAt(_).toOption.toSeq)
            .filter(!_.getPsi.isInstanceOf[PsiWhiteSpace]).map(_.getPsi).headOption

    (firstLeafOf(span), firstLeafOf(span.reverse))
  }
}