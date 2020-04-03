package org.jetbrains.plugins.scala.worksheet.ui.printers.repl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi.PrintChunk

sealed trait QueuedPsi {

  /** @return underlying psi(-s) is valid */
  final def isValid: Boolean = inReadAction(isValidImpl)
  protected def isValidImpl: Boolean

  /** @return the whole corresponding input text */
  final def getText: String = inReadAction(getTextImpl)
  protected def getTextImpl: String

  /** @return input text range */
  def getWholeTextRange: TextRange

  /** @param output the whole output from the interpreter */
  def getPrintChunks(output: String): Seq[QueuedPsi.PrintChunk]

  def getFirstProcessedOffset: Int
  def getLastProcessedOffset: Int = getFirstProcessedOffset

  protected def computeStartPsi(psi: PsiElement): PsiElement = {
    val actualStart = psi.getFirstChild match {
      case comment: PsiComment =>
        var c = comment.getNextSibling
        while (c.is[PsiComment, PsiWhiteSpace]) c = c.getNextSibling
        if (c != null) c else psi
      case _ => psi
    }

    actualStart
  }

  protected def startPsiOffset(psi: PsiElement): Int = computeStartPsi(psi).startOffset

  protected def getPsiTextWithCommentLine(psi: PsiElement): String =
    getPsiTextWithCommentLine(psi.getText)

  protected def getPsiTextWithCommentLine(text: String): String =
    storeLineInfoRepl(text.linesIterator.toIterable)

  protected def storeLineInfoRepl(lines: Iterable[String]): String = {
    lines.zipWithIndex
      .map { case (line, index) => s"$line //$index" }
      .mkString("\n")
  }
}

object QueuedPsi {

  case class PrintChunk(
    absoluteOffset: Int, // offset in input document,
    relativeOffset: Int, // offset of current chunk from the previous  chunk
    text: String // chunk output  text
  )
}


case class SingleQueuedPsi(psi: PsiElement) extends QueuedPsi {
  override protected def isValidImpl: Boolean = psi.isValid

  override protected def getTextImpl: String = getPsiTextWithCommentLine(psi)

  override def getWholeTextRange: TextRange = psi.getTextRange

  override def getPrintChunks(output: String): Seq[PrintChunk] = Seq(PrintChunk(startPsiOffset(psi), 0, output))

  override def getFirstProcessedOffset: Int = startPsiOffset(psi)
}

/** @param clazz class or trait */
case class ClassObjectPsi(
  clazz: ScTypeDefinition,
  obj: ScObject,
  mid: String,
  isClazzFirst: Boolean
) extends QueuedPsi {
  val (first, second) = if (isClazzFirst) (clazz, obj) else (obj, clazz)

  override protected def isValidImpl: Boolean = clazz.isValid && obj.isValid

  override protected def getTextImpl: String = getPsiTextWithCommentLine(first) + mid + getPsiTextWithCommentLine(second)

  override def getWholeTextRange: TextRange = new TextRange(first.startOffset, second.endOffset)

  override def getPrintChunks(output: String): Seq[PrintChunk] = {
    //we assume output is `class A defined<new_line>object A defined`
    val newLineIdx = output.indexOf('\n')
    val (text1, text2) =
      if (newLineIdx == -1) (output, "")
      else output.splitAt(newLineIdx)

    val offset1 = startPsiOffset(first)
    val offset2 = startPsiOffset(second)

    val chunk1 = PrintChunk(offset1, 0, text1)
    val chunk2 = PrintChunk(offset2, offset2 - offset1, text2.trim)
    Seq(chunk1, chunk2)
  }

  override def getFirstProcessedOffset: Int = startPsiOffset(first)

  override def getLastProcessedOffset: Int = startPsiOffset(second)
}

/** represents a sequence of input psi elements that go on a single line and separated with a semicolon  */
case class SemicolonSeqPsi(elements: Seq[PsiElement]) extends QueuedPsi {
  override protected def isValidImpl: Boolean = elements.nonEmpty && elements.forall(_.isValid)

  override protected def getTextImpl: String = {
    val concat = elements.map(_.getText).mkString(" ; ")
    getPsiTextWithCommentLine(concat)
  }

  override def getWholeTextRange: TextRange = TextRange.create(elements.head.startOffset, elements.last.endOffset)

  override def getPrintChunks(output: String): Seq[PrintChunk] = {
    val offset = startPsiOffset(elements.head)
    val chunk = PrintChunk(offset, 0, output)
    Seq(chunk)
  }

  override def getFirstProcessedOffset: Int = startPsiOffset(elements.head)
  override def getLastProcessedOffset: Int = startPsiOffset(elements.last)
}
