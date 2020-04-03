package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.{CalledInAwt, CalledWithWriteLock}
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter.MessageLineParsed
import org.jetbrains.plugins.scala.compiler.data.worksheet.ReplMessages
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaPluginUtils}
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.CompilerMessagesConsumer
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterBase.FoldingOffsets
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi.PrintChunk

import scala.collection.mutable

//noinspection HardCodedStringLiteral
final class WorksheetEditorPrinterRepl private[printers](
  editor: Editor,
  viewer: Editor,
  file: ScalaFile
) extends WorksheetEditorPrinterBase(editor, viewer) {

  import ReplMessages._
  import WorksheetEditorPrinterRepl._

  // Mapping of successfully-processed chunks from left editor to the output end in the right editor
  private val inputToOutputMapping = mutable.ArrayBuffer.empty[InputOutputMappingItem]
  /**  @return index of the last processed line */
  def lastProcessedLine: Option[Int] = inputToOutputMapping.lastOption.map(_.inputLine)
  def resetLastProcessedLine(): Unit = inputToOutputMapping.clear()

  private var currentFile: ScalaFile = file
  override def getScalaFile: ScalaFile = currentFile
  def updateScalaFile(file: ScalaFile): Unit = currentFile = file

  /* we have to inject this interface because we have to restore the original error positions in worksheet editor
   * and for now this can only be done in this printer  */
  private var messagesConsumerOpt: Option[CompilerMessagesConsumer] = None
  def updateMessagesConsumer(consumer: CompilerMessagesConsumer): Unit = messagesConsumerOpt = Some(consumer)

  private val psiToProcess = mutable.Queue.empty[QueuedPsi]
  def updateEvaluatedElements(evaluatedElements: Seq[QueuedPsi]): Unit = {
    psiToProcess.clear()
    psiToProcess ++= evaluatedElements
  }

  private val chunkOutputBuffer = StringBuilder.newBuilder
  private var chunkIsBeingProcessed = false

  private def debug(text: => String): Unit =
    println(s"[${Thread.currentThread.getId}] $text")

  // FIXME: now all return boolean values are not processed anywhere and do not mean anything, remove or handle
  // FIXME: handle exceptions in process line
  // TODO: better to abstract away from "line string" to some kind of message / event, wrap the line
  //  we already WorksheetEditorPrinterRepl.ReplMessage.unapply, could generalize over all type of output
  override def processLine(line: String): Boolean = chunkOutputBuffer.synchronized {
    if (!isInited) init()
    //debug(s"line: '" + line.replaceAll("[\n\r]", "\\\\n ") + "'")

    val command = line.trim
    command match {
      case ReplStart =>
        prepareViewerDocument()
        if (lastProcessedLine.isEmpty)
          cleanFoldingsLater()
        chunkOutputBuffer.clear()
        false
      case ReplEnd   =>
        flushBuffer()
        updateLastLineMarker()
        true

      case ReplChunkStart =>
        chunkIsBeingProcessed = true
        false
      case ReplChunkEnd | ReplChunkCompilationError =>
        chunkIsBeingProcessed = false
        //prepareViewerDocument()

        val outputText = chunkOutputBuffer.toString.trimRight
        chunkOutputBuffer.clear()

        val successfully = command == ReplChunkEnd
        chunkProcessed(outputText, successfully)

        updateLastLineMarker()

        !successfully
      case ReplMessage(line) =>
        handleReplMessageLine(line)
        false
      case _ =>
        if (chunkIsBeingProcessed)
          chunkOutputBuffer.append(adjustOutputLineContent(line))
        false
    }
  }

  private def prepareViewerDocument(): Unit =
    inputToOutputMapping.lastOption.map(_.outputLastLine) match {
      case Some(outputLine) =>
        val nextLine = outputLine + 1
        if (viewerDocument.getLineCount > nextLine)
          cleanViewer(fromLineIdx = nextLine)
      case _                =>
        cleanViewer()
    }

  private def cleanViewer(fromLineIdx: Int = 0): Unit =
    inWriteCommandAction {
      //debug(s"cleanViewer: $fromLineIdx")
      if (fromLineIdx == 0) {
        simpleUpdate("", viewerDocument)
        cleanFoldings()
      } else {
        val start = (viewerDocument.getLineStartOffset(fromLineIdx) - 1).max(0) // capture previous new line as well
        val end   = viewerDocument.getTextLength
        viewerDocument.deleteString(start, end)
      }
    }

  override def close(): Unit = {}

  //nothing to flush, content is flushed inside chunkProcessed, not expecting any other unflushed content content
  override def flushBuffer(): Unit = {}

  // Looks like we don't need any flushing here
  override def scheduleWorksheetUpdate(): Unit = {}

  private def chunkProcessed(outputText: String, successfully: Boolean): Unit = {
    if (psiToProcess.isEmpty) {
      // not expecting to be empty, elements count in original psiToProcess should be equal to number of executed  commands in REPL
      //noinspection ScalaExtractStringToBundle
      if (ScalaPluginUtils.isRunningFromSources)
        NotificationUtil.showMessage(project, "psiToProcess is empty")
      return
    }

    val queuedPsi: QueuedPsi = psiToProcess.dequeue()
    if (!queuedPsi.isValid) {
      // This case can be observed if between "run worksheet" action and end of the worksheet evaluation output
      // user decided to change some psi element in left editor, or even clean editor content completely.
      // We can't detect the original line number correctly in this situation, so for now just ignoring any output of such chunks
      return
    }

    invokeAndWait {
      inWriteAction {
        chunkProcessed(queuedPsi, outputText, successfully)
      }
    }
  }

  @CalledInAwt
  @CalledWithWriteLock
  private def chunkProcessed(queuedPsi: QueuedPsi, outputText: String, successfully: Boolean): Unit = {
    @inline
    /** @return lines index (0-based) */
    def originalLine(offset: Int): Int = originalDocument.getLineNumber(offset)

    val chunkTextRange = inReadAction(queuedPsi.getWholeTextRange)

    val inputBeginningStartLine = originalLine(queuedPsi.getFirstProcessedOffset)
    val inputBeginningEndLine   = originalLine(queuedPsi.getLastProcessedOffset)
    val inputEndLine            = originalLine(chunkTextRange.getEndOffset)

    ///////////////////////////////////////

    val currentOutput = new mutable.StringBuilder()

    // 1) append blank lines indentation to align input line from left editor with output line from right editor

    // a single visible line can actually contain many folded lines, so actual indexes can shift further
    // but the used does not see those folded lines so we need to extract folded lines
    val numberOfFoldedLines = foldGroup.foldedLines
    val lastProcessedChunkOutputLine = inputToOutputMapping.lastOption.map(_.outputLastLine).getOrElse(0)
    val viewerDocumentLastVisibleLine = (lastProcessedChunkOutputLine - numberOfFoldedLines).max(0)

    val prefixBlankLinesCount = (inputBeginningStartLine - viewerDocumentLastVisibleLine).max(0)
    val prefixBlankLinesString = buildNewLines(prefixBlankLinesCount)
    currentOutput.append(prefixBlankLinesString)

    // 2) append current queuedPsi evaluation output

    val outputChunks = queuedPsi.getPrintChunks(outputText)
    val outputTextWithNewLinesOffset = outputChunks.map { case PrintChunk(absoluteOffset, relativeOffset, chunkText) =>
      val currChunkLine = originalLine(absoluteOffset)
      val prevChunkLine = originalLine(absoluteOffset - relativeOffset)
      val linesBetween = currChunkLine - prevChunkLine
      (chunkText, linesBetween)
    }
    outputTextWithNewLinesOffset.foreach { case (chunkText, newLinesOffset) =>
      val prefix = buildNewLines(newLinesOffset)
      currentOutput.append(prefix)
      currentOutput.append(chunkText)
    }

    simpleAppend(currentOutput, viewerDocument)

    val blankLinesFromOutput = outputTextWithNewLinesOffset.foldLeft(0)(_ + _._2)

    // do not update mapping / folding for first chunk which failed to be evaluated (compilation error/exception/etc...)
    // consider that tailed chunk will be the last in current worksheet run session
    if (successfully) {
      WorksheetAutoRunner.getInstance(project).replExecuted(originalDocument, chunkTextRange.getEndOffset)

      val inputLineCount  = linesCount(queuedPsi.getText)
      val outputLineCount = linesCount(outputText)

      val mapping = InputOutputMappingItem(
        inputBeginningEndLine,
        lastProcessedChunkOutputLine + prefixBlankLinesCount + (outputLineCount - 1).max(0) + (blankLinesFromOutput - 1).max(0)
      )
      inputToOutputMapping.append(mapping)

      if (outputLineCount > inputLineCount) {
        val lineCount = viewerDocument.getLineCount

        val outputStartLine = lastProcessedChunkOutputLine + prefixBlankLinesCount
        val outputEndOffset = viewerDocument.getLineEndOffset(lineCount - 1)

        val foldings = FoldingOffsets(
          outputStartLine,
          outputEndOffset,
          inputLineCount,
          inputEndLine
        )
        updateFoldings(Seq(foldings))
        //debug(s"foldings: $foldings")
      }

      saveEvaluationResult(viewerDocument.getText)
    }

    // debug(s"mapping: ${inputToOutputMapping.map(InputOutputMappingItem.unapply(_).get)}")
  }

  private def adjustOutputLineContent(outputLine: String): String =
    adjustLambdaDefinitionOutput(outputLine)

  /**
   * Handles lambda functions definitions
   * for input: `val f: (Int) => Boolean = _ == 42`
   * prints: `f: Int => Boolean = <function>134087`
   * instead of: `f: Int => Boolean = $$Lambda$2299/0x000000010152a040@747dd14a`
   */
  private def adjustLambdaDefinitionOutput(outputLine: String): String =
    outputLine.indexOf(LAMBDA_PREFIX) match {
      case -1 => outputLine
      case idx =>
        val prefix = outputLine.substring(0, Math.max(idx - 1, 0))
        val suffix = outputLine.substring(Math.min(outputLine.length, LAMBDA_LENGTH + idx + 1))
        prefix + "<function>" + suffix
    }

  private def handleReplMessageLine(messageLine: String): Unit = {
    val messagesConsumer = messagesConsumerOpt.getOrElse(return)
    val currentPsi = psiToProcess.headOption.getOrElse(return)
    val compilerMessage = buildCompilerMessage(messageLine, currentPsi)
    messagesConsumer.message(compilerMessage)
  }

  private def buildCompilerMessage(messageLine: String, currentPsi: QueuedPsi): CompilerMessage = {
    val replMessageInfo = extractReplMessage(messageLine)
      .getOrElse(ReplMessageInfo(messageLine, "", 0, 0, CompilerMessageCategory.INFORMATION))

    val ReplMessageInfo(message, lineContent, lineOffset, columnOffset, severity) = replMessageInfo

    val (hOffset, vOffset) = extraOffset(WorksheetFileSettings(getScalaFile).getModuleFor)
    val columnOffsetFixed = columnOffset - vOffset
    val (lineContentClean, lineOffsetFinal) = splitLineNumberFromRepl(lineContent).getOrElse {
      (lineContent, lineOffset - hOffset)
    }

    val messagePosition: LogicalPosition = {
      val elementPosition = inReadAction {
        val offset = currentPsi.getWholeTextRange.getStartOffset
        originalEditor.offsetToLogicalPosition(offset)
      }

      new LogicalPosition(
        (elementPosition.line + lineOffsetFinal).max(0),
        (elementPosition.column + columnOffsetFixed).max(0)
      )
    }

    val messageLines = (message.split('\n'):+ lineContentClean).map(_.trim).filter(_.length > 0)
    new CompilerMessageImpl(
      project,
      severity,
      messageLines.mkString("\n"),
      file.getVirtualFile,
      messagePosition.line + 1, // compiler messages positions are 1-based
      messagePosition.column + 1,
      null
    )
  }

  private def extractReplMessage(messageLine: String): Option[ReplMessageInfo] =
    PrintWriterReporter.parse(messageLine).map {
      case MessageLineParsed(severity, line, column, lineContent, message) =>
        val messageCategory = severity match {
          case "INFO"    => CompilerMessageCategory.INFORMATION
          case "ERROR"   => CompilerMessageCategory.ERROR
          case "WARNING" => CompilerMessageCategory.WARNING
          case _         => CompilerMessageCategory.INFORMATION
        }
        ReplMessageInfo(message, lineContent, (line - 1).max(0), (column - 1).max(0), messageCategory)
    }

  private def updateLastLineMarker(): Unit = inReadAction {
    DaemonCodeAnalyzer.getInstance(project).restart(getScalaFile)
  }
}

object WorksheetEditorPrinterRepl {

  private val LAMBDA_PREFIX = "$Lambda$"
  private val LAMBDA_LENGTH = 32

  // Required due to compiler reports wrong error positions with extra offsets which we need to fix.
  // This happens because compiler prepossesses original input adding extra classes, indents, imports, etc...
  // Ideally lines from compiler (see extractReplMessage) should be relative to the original input
  // but unfortunately old scala versions does not provide such API
  private def extraOffset(module: Module): (Int, Int) = {
    import project._
    import ScalaLanguageLevel._

    val sdk = module.scalaSdk
    val languageLevel = sdk.map(_.properties.languageLevel)
    val compilerVersion = sdk.flatMap(_.compilerVersion)

    val consoleHeaders = languageLevel.map {
      case Scala_2_9 | Scala_2_10                                        => 7
      case Scala_2_11 if compilerVersion.forall(!_.startsWith("2.11.8")) => 7
      case Scala_2_13                                                    => 0
      case _                                                             => 11
    }

    val verticalOffset   = consoleHeaders
    val horizontalOffset = languageLevel.map {
      case Scala_2_11 => 7
      case _          => 0
    }


    (verticalOffset.getOrElse(0), horizontalOffset.getOrElse(0))
  }

  def countNewLines(str: String): Int = StringUtil.countNewLines(str)
  private def linesCount(str: String): Int = countNewLines(str) + 1

  private case class ReplMessageInfo(text: String,
                                     lineContent: String,
                                     lineOffset: Int,
                                     columnOffset: Int,
                                     messageCategory: CompilerMessageCategory)

  object ReplMessage {

    private val CONSOLE_REPORT_PREFIX = PrintWriterReporter.IJReportPrefix

    def unapply(line: String): Option[String] =
      if (line.startsWith(CONSOLE_REPORT_PREFIX)) {
        Option(line.substring(CONSOLE_REPORT_PREFIX.length).trim)
      } else {
        None
      }
  }

  private def splitLineNumberFromRepl(line: String): Option[(String, Int)] =
    line.lastIndexOf("//") match {
      case -1 => None
      case commentIdx =>
        val (content, comment) =  line.splitAt(commentIdx)
        for {
          lineIdx <- comment.substring(2).trim.toIntOpt
        } yield (content, lineIdx)
    }

  /**
   * @param inputLine      0-based input chunk start line
   * @param outputLastLine 0-based output last line inclusive
   */
  private case class InputOutputMappingItem(inputLine: Int, outputLastLine: Int)
}
