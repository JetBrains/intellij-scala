package org.jetbrains.plugins.scala.lang

import com.intellij.psi.{PsiElement, PsiFile}

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
    def readableString: String = s"${line + 1}:${col + 1}"
  }

  def textPosOf(e: PsiElement): TextPos = textPosOf(e.getTextOffset, e.getContainingFile)
  def textPosOf(offset: Int, file: PsiFile): TextPos = {
    val fileViewProvider = file.getViewProvider
    val document = fileViewProvider.getDocument
    val line = document.getLineNumber(offset)
    val col = offset - document.getLineStartOffset(line)
    TextPos(line, col)
  }
}
