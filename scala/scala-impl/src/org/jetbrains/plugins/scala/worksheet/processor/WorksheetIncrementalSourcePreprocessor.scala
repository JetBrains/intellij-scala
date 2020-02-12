package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiErrorElement
import com.intellij.util.Base64
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache

object WorksheetIncrementalSourcePreprocessor {

  private val REPL_DELIMITER = "\n$\n$\n"

  // can consist of code snippets (val x = 42) or native REPL commands (:reset, :help)
  case class PreprocessResult(commandsEncoded: String)

  def preprocess(srcFile: ScalaFile, editor: Editor): Either[PsiErrorElement, PreprocessResult] = {
    val lastProcessed = WorksheetCache.getInstance(srcFile.getProject).getLastProcessedIncremental(editor)

    val glue = WorksheetPsiGlue()
    val iterator = new WorksheetInterpretExprsIterator(srcFile, Some(editor), lastProcessed)
    iterator.collectAll(
      x => inReadAction(glue.processPsi(x)),
      Some(e => return Left(e))
    )
    val elements = glue.result
    val codeCommands = elements.map(_.getText)

    val needToReset = lastProcessed.isEmpty
    val additionalCommands = if (needToReset) Seq(":reset") else Nil

    val replCommands = additionalCommands ++ codeCommands
    val commands = replCommands.mkString(REPL_DELIMITER)
    val commandsEncoded = Base64.encode(commands.getBytes)
    Right(PreprocessResult(commandsEncoded))
  }
}
