package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

private class WorksheetInterpretExprsIterator(
  file: ScalaFile,
  document: Document,
  lastProcessedLine: Option[Int]
) extends Iterator[Either[PsiErrorElement, PsiElement]] {

  import WorksheetInterpretExprsIterator._

  private val firstElement = inReadAction {
    firstElementToProcess(lastProcessedLine)(file, document)
  }

  var current: PsiElement = firstElement.orNull
  override def hasNext: Boolean = current != null
  override def next(): Either[PsiErrorElement, PsiElement] =
    current match {
      case error: PsiErrorElement =>
        current = null
        Left(error)
      case other =>
        current = current.getNextSibling
        Right(other)
    }
}

private object WorksheetInterpretExprsIterator {

  private def firstElementToProcess(lastProcessedLine: Option[Int])
                                   (implicit file: PsiFile, document: Document): Option[PsiElement] =
    lastProcessedLine match {
      case Some(line) =>
        val topMost = topMostElementAtLine(line)
        topMost.flatMap(_.nextSibling)
      case None        =>
        Option(file.getFirstChild)
    }

  private def topMostElementAtLine(lastProcessedLine: Int)
                                  (implicit file: PsiFile, document: Document): Option[PsiElement] =
    for {
      element <- findElementAtLine(lastProcessedLine)
      topMost <- element.withParentsInFile.lastOption
    } yield topMost

  private def findElementAtLine(line: Int)
                               (implicit file: PsiFile, document: Document): Option[PsiElement] = {
    val (start, end) = document.lineRange(line)
    Option(file.findElementAt((start + end) / 2))
  }

  implicit class DocumentExt(private val document: Document) extends AnyVal {

    def lineRange(lineIdx: Int): (Int, Int) =
      (document.getLineStartOffset(lineIdx), document.getLineEndOffset(lineIdx))
  }
}
