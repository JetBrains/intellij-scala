package org.jetbrains.plugins.scala.lang

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement

import scala.meta.internal.semanticdb

package object resolveSemanticDb {

  implicit class RangeOps(private val range: semanticdb.Range) extends AnyVal {
    def contains(pos: TextPos): Boolean =
      range.startLine <= pos.line && pos.line <= range.endLine &&
        range.startCharacter <= pos.col && pos.col < range.endCharacter

    def mkString: String =
      s"${range.startLine}:${range.startCharacter}..${range.endLine}:${range.endCharacter}"
  }

  case class TextPos(line: Int, col: Int) {
    override def toString: String = s"${line + 1}:${col + 1}"
  }

  def textPosOf(e: PsiElement): TextPos = textPosOf(e.getTextOffset, e.getContainingFile)
  def textPosOf(offset: Int, file: PsiFile): TextPos = {
    if (offset < 0) {
      return TextPos(-1, -1)
    }
    val offsetText = file.getText.substring(0, offset)
    val line = offsetText.count(_ == '\n')
    val col = offset - (offsetText.lastIndexOf('\n') + 1)
    TextPos(line, col)
  }

  def isInRefinement(e: PsiElement): Boolean = e.contexts.exists(_.is[ScRefinement])
}
