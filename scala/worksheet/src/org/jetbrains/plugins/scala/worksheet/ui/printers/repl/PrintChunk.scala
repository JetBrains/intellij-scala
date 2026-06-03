package org.jetbrains.plugins.scala.worksheet.ui.printers.repl

import com.intellij.psi.PsiElement
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.Seq

/**
 * @param lineOffset relative offset of chunk output text (in lines) after previous chunk
 * @param text chunk output  text
 */
case class PrintChunk(lineOffset: Int, text: String)

object PrintChunk {

  /**
   * @param output                         the whole output from the interpreter for current queued psi
   * @param originalDocumentLineFromOffset function returning line number in the original document from document offset
   *                                       original line can be empty for some part of queued psi, for example
   *                                       if the original original document was changed during evaluation
   *                                       e.g. in class + companion object definition user can remove just class or
   *                                       object during evaluation
   */
  def buildChunksFor(
    queuedPsi: QueuedPsi,
    output: String,
    originalDocumentLineFromOffset: Int => Option[Int]
  ): Seq[PrintChunk] = {
    queuedPsi match {
      case QueuedPsi.QueuedPsiSeq(_) =>
        singlePrintChunk(output)
      case QueuedPsi.RelatedTypeDefs(typedefs) =>
        val outputLines = output.linesIterator.filter(StringUtils.isNotBlank).toSeq
        val result = if (typedefs.size == outputLines.size) {
          // assuming one definition = one output line (e.g. defined class)
          oneLinePerElement(typedefs, outputLines, originalDocumentLineFromOffset)
        } else {
          // in some cases number of output lines can be more then output definitions (e.g. in Scala3 all warnings go to output)
          singlePrintChunk(output)
        }
        result
    }
  }

  private def singlePrintChunk(output: String): Seq[PrintChunk] =
    Seq(PrintChunk(0, output))

  private def oneLinePerElement(
    elements: Iterable[PsiElement],
    outputLines: Iterable[String],
    originalDocumentLineFromOffset: Int => Option[Int],
  ): Seq[PrintChunk] = {
    val offsets = elements.map(QueuedPsi.psiContentOffset)
    val lines = offsets.map(originalDocumentLineFromOffset)
    val linesOffsets = Iterator(0) ++ lines.sliding(2).map {
      case Seq(Some(prev), Some(curr)) => curr - prev // original content hasn't changed
      case _                           => 1 // some chunk was e.g. removed
    }
    linesOffsets.zip(outputLines.iterator).map((PrintChunk.apply _).tupled).toSeq
  }
}