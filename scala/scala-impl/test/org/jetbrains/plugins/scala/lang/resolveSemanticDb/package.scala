package org.jetbrains.plugins.scala.lang

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement

import scala.meta.internal.semanticdb

package object resolveSemanticDb {

  implicit class RangeOps(private val range: (TextPos, TextPos)) extends AnyVal {
    def contains(pos: TextPos): Boolean =
      range._1.line <= pos.line && pos.line <= range._2.line &&
        range._1.col <= pos.col && pos.col < range._2.col

    def isEmpty: Boolean =
      range._1 == range._2

    def is(pos: TextPos): Boolean =
      range.isEmpty && range._1 == pos

  }

  case class TextPos(line: Int, col: Int) {
    override def toString: String = s"$line:$col"
  }

  object TextPos {
    def fromZeroBased(line: Int, col: Int): TextPos =
      TextPos(line + 1, col + 1)
    def ofStart(range: semanticdb.Range): TextPos =
      fromZeroBased(range.startLine, range.startCharacter)
    def ofEnd(range: semanticdb.Range): TextPos =
      fromZeroBased(range.endLine, range.endCharacter)
    def of(e: PsiElement): TextPos = at(e.getTextOffset, e.getContainingFile)
    def at(offset: Int, file: PsiFile): TextPos = {
      if (offset < 0) {
        return TextPos(-1, -1)
      }
      val offsetText = file.getText.substring(0, offset)
      val line = offsetText.count(_ == '\n')
      val lastLineStart = offsetText.lastIndexOf('\n') + 1
      val col = offset - lastLineStart
      TextPos.fromZeroBased(line, col)
    }

    implicit val ordering: Ordering[TextPos] = Ordering.by[TextPos, Int](_.line).orElseBy(_.col)
  }

  def isInRefinement(e: PsiElement): Boolean = e.contexts.exists(_.is[ScRefinement])
}
