package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiErrorElement
import com.intellij.util.Base64
import org.jetbrains.plugins.scala.compiler.data.worksheet.ReplMessages.ReplDelimiter
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi

object WorksheetIncrementalSourcePreprocessor {

  // can consist of code snippets (e.g. `val x = 42; println(23);`) or native REPL commands (e.g. :reset, :help)
  case class PreprocessResult(commandsEncoded: String, evaluatedElements: Seq[QueuedPsi])

  def preprocess(srcFile: ScalaFile, editor: Editor): Either[PsiErrorElement, PreprocessResult] = {
    val lastProcessedLine = WorksheetCache.getInstance(srcFile.getProject).getLastProcessedIncremental(editor)

    val iterator = new WorksheetInterpretExprsIterator(srcFile, editor.getDocument, lastProcessedLine)
    val elementsToEvaluate = toEitherOfSeq(iterator.toSeq) match {
      case Left(errorElement) =>
        return Left(errorElement)
      case Right(elements) =>
        inReadAction {
          new WorksheetPsiGlue().prepareEvaluatedElements(elements)
        }
    }
    val commands = {
      val fileText = inReadAction(srcFile.getText)
      val codeCommands = elementsToEvaluate.map(_.textRange.substring(fileText))

      val needToReset = lastProcessedLine.isEmpty
      val additionalCommands = if (needToReset) Seq(":reset") else Nil

      (additionalCommands ++ codeCommands).mkString(ReplDelimiter)
    }
    val commandsEncoded = Base64.encode(commands.getBytes)
    Right(PreprocessResult(commandsEncoded, elementsToEvaluate))
  }

  private def toEitherOfSeq[A, B](seq: Seq[Either[A, B]]): Either[A, Seq[B]] =
    seq.find(_.isLeft) match {
      case Some(value) => Left(value.left.get)
      case None        => Right(seq.map(_.right.get))
    }
}
