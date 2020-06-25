package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.editor.{Document, Editor, LogicalPosition}
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.{CalledInAwt, CalledWithWriteLock}
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter.MessageLineParsed
import org.jetbrains.plugins.scala.compiler.data.worksheet.ReplMessages
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.project
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaPluginUtils}
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.CompilerMessagesConsumer
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterBase.InputOutputFoldingInfo
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.{PrintChunk, QueuedPsi}

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
  def lastProcessedLine: Option[Int] = inputToOutputMapping.lastOption.map(_.inputLinesInfo.lastElementLine)
  // can be different from number of lines in viewerDocument cause document can contains errors in the end
  private def lastProcessedOutputLine: Option[Int] = inputToOutputMapping.lastOption.map(_.outputLinesInfo.outputEndLine)
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

        val outputText = chunkOutputBuffer.toString.replaceFirst("\\s++$", "")
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
    lastProcessedOutputLine match {
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
        simpleUpdate(viewerDocument, "")
        cleanFoldings()
      } else {
        val start = (viewerDocument.getLineStartOffset(fromLineIdx) - 1).max(0) // capture previous new line as well
        val end   = viewerDocument.getTextLength
        viewerDocument.deleteString(start, end)
      }
    }

  override def close(): Unit = {}

  //nothing to flush, content is flushed inside chunkProcessed, not expecting any other not-flushed content
  override def flushBuffer(): Unit = {}

  // Looks like we don't need any flushing here
  override def scheduleWorksheetUpdate(): Unit = {}

  override def internalError(ex: Throwable): Unit = {
    val fullErrorMessage = internalErrorMessage(ex)
    if (!chunkProcessed(fullErrorMessage, successfully = false)) {
      // no queued psi for the error for some reason (e.g. if error occurred in the end of the processing of all chunks)
      invokeAndWait {
        inWriteAction {
          simpleAppend(viewerDocument, "\n" +  fullErrorMessage)
        }
      }
    }
  }

  private def popCurrentQueuedPsi(): Option[QueuedPsi] = {
    if (psiToProcess.isEmpty) {
      // not expecting to be empty, elements count in original psiToProcess should be equal to number of executed  commands in REPL
      //noinspection ScalaExtractStringToBundle
      if (ScalaPluginUtils.isRunningFromSources)
        NotificationUtil.showMessage(project, "psiToProcess is empty")
      return None
    }

    val queuedPsi: QueuedPsi = psiToProcess.dequeue()
    if (!queuedPsi.isValid) {
      // This case can be observed if between "run worksheet" action and end of the worksheet evaluation output
      // user decided to change some psi element in left editor, or even clean editor content completely.
      // We can't detect the original line number correctly in this situation, so for now just ignoring any output of such chunks
      return None
    }
    Some(queuedPsi)
  }

  private def chunkProcessed(outputText: String, successfully: Boolean): Boolean =
    invokeAndWait {
      inWriteAction {
        popCurrentQueuedPsi() match {
          case Some(queuedPsi) =>
            chunkProcessed(queuedPsi, outputText, successfully)
            true
          case None =>
            false
        }
      }
    }

  @CalledInAwt
  @CalledWithWriteLock
  private def chunkProcessed(queuedPsi: QueuedPsi, outputText: String, successfully: Boolean): Unit = {
    val inputLinesInfo  = buildInputLinesInfo(queuedPsi)
    val outputInfo      = buildOutputInfo(inputLinesInfo, queuedPsi, outputText)
    val outputLinesInfo = outputInfo.linesInfo

    // Adding new lines before the actual chunk output to align input line from left editor with output line from right editor

    simpleAppend(viewerDocument, buildNewLines(outputLinesInfo.prefixNewLines))
    simpleAppend(viewerDocument, outputInfo.actualOutputText)

    // do not update mapping / folding for first chunk which failed to be evaluated (compilation error/exception/etc...)
    // consider that tailed chunk will be the last in current worksheet run session
    if (successfully) {
      val mapping = InputOutputMappingItem(inputLinesInfo, outputLinesInfo)
      inputToOutputMapping.append(mapping)

      val needsFolding = outputLinesInfo.outputLinesCount > inputLinesInfo.inputLinesCount
      if (needsFolding) {
        val foldingInfo = buildFoldingInfo(inputLinesInfo, outputLinesInfo)
        //debug(s"foldings: $foldings")
        updateFoldings(foldingInfo)
      }

      WorksheetAutoRunner.getInstance(project).replExecuted(originalDocument, inputLinesInfo.range.getEndOffset)
      saveEvaluationResult(viewerDocument.getText)
    }

    // debug(s"mapping: ${inputToOutputMapping.map(InputOutputMappingItem.unapply(_).get)}")
  }

  private def originalLine(offset: Int): Option[Int] = originalDocument.lineNumberSafe(offset)

  private def buildInputLinesInfo(queuedPsi: QueuedPsi): InputLinesInfo = {
    val range = queuedPsi.textRange

    InputLinesInfo(
      originalLine(queuedPsi.firstElementOffset).getOrElse(0),
      originalLine(queuedPsi.lastElementOffset).getOrElse(0),
      originalLine(range.getStartOffset).getOrElse(0),
      originalLine(range.getEndOffset).getOrElse(0),
      range
    )
  }

  private def buildOutputInfo(inputInfo: InputLinesInfo, queuedPsi: QueuedPsi, outputText: String): OutputInfo = {
    val actualOutputText = buildActualOutputText(queuedPsi, outputText)
    val linesInfo = buildOutputLinesInfo(inputInfo, actualOutputText)
    OutputInfo(linesInfo, actualOutputText)
  }

  private def buildOutputLinesInfo(inputInfo: InputLinesInfo, actualOutputText: String): OutputLinesInfo = {
    // a single visible line can actually contain many folded lines,
    // but user does not see those folded lines so we need to subtract them
    val foldedLinesCount = foldGroup.foldedLinesCount
    val lastChunkOutputLine = lastProcessedOutputLine.getOrElse(0)
    val lastChunkOutputVisibleLine = (lastChunkOutputLine - foldedLinesCount).max(0)

    val prefixNewLines = (inputInfo.firstElementLine - lastChunkOutputVisibleLine).max(0)
    val outputStartLine = lastChunkOutputLine + prefixNewLines
    val outputEndLine = outputStartLine + StringUtil.countNewLines(actualOutputText)

    OutputLinesInfo(
      prefixNewLines,
      outputStartLine,
      outputEndLine
    )
  }

  private def buildActualOutputText(queuedPsi: QueuedPsi, outputText: String): String = {
    val printChunks = PrintChunk.buildChunksFor(queuedPsi, outputText, originalLine)
    //debug("printChunks: " + printChunks.toArray.toSeq)
    val buffer = new mutable.StringBuilder()
    printChunks.foreach { case PrintChunk(linesBetween, text) =>
      buffer.append(buildNewLines(linesBetween))
      buffer.append(text)
    }
    buffer.toString()
  }

  private def buildFoldingInfo(inputLines: InputLinesInfo, outputLines: OutputLinesInfo) =
    InputOutputFoldingInfo(
      inputLines.firstElementLine,
      inputLines.contentEndLine,
      outputLines.outputStartLine,
      outputLines.outputEndLine
    )

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

  private def buildCompilerMessage(messageLine: String, chunk: QueuedPsi): CompilerMessage = {
    val replMessageInfo = extractReplMessage(messageLine)
      .getOrElse(ReplMessageInfo(messageLine, "", 0, 0, CompilerMessageCategory.INFORMATION))

    val ReplMessageInfo(message, lineContent, lineOffset, columnOffset, severity) = replMessageInfo

    val module = WorksheetFileSettings(getScalaFile).getModuleFor
    val sdk = module.scalaSdk
    val (hOffset, vOffset) = sdk.map(extraOffset(_, chunk)).getOrElse((0, 0))

    val columnOffsetFixed  = columnOffset - vOffset
    val lineOffsetFixed    = lineOffset - hOffset

    val messagePosition: LogicalPosition = {
      val elementPosition = inReadAction {
        val offset = chunk.textRange.getStartOffset
        originalEditor.offsetToLogicalPosition(offset)
      }

      val line   = elementPosition.line + lineOffsetFixed
      val column = elementPosition.column + columnOffsetFixed
      new LogicalPosition(line.max(0), column.max(0))
    }

    val messageLines = (message.split('\n'):+ lineContent).map(_.trim).filter(_.length > 0)
    new CompilerMessageImpl(
      project,
      severity,
      messageLines.mkString("\n"),
      file.getVirtualFile,
      // compiler messages positions are 1-based
      // (NOTE: Scala 3 doesn't report errors at this moment, it prints them to stdout/err)
      messagePosition.line + 1,
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
  private def extraOffset(scalaSdk: LibraryEx, chunk: QueuedPsi): (Int, Int) = {
    import project._
    import ScalaLanguageLevel._

    val languageLevel = scalaSdk.properties.languageLevel

    // this hacks takes into account only major versions
    val consoleHeaders = languageLevel match {
      case Scala_2_13             => 0 // looks like scala 13 reports errors fine, no hacks needed
      case Scala_2_9 | Scala_2_10 => 7
      case _                      =>
        // for any definition, val, var, class, trait, etc... error positions are shifted by one (at least what I observed)
        val hasSomeDefinition = chunk.getElements.exists(_.is[ScMember, ScValueOrVariable])
        val definitionOffset = if (hasSomeDefinition) 1 else 0 //
        11 - definitionOffset
    }

    val verticalOffset   = consoleHeaders
    val horizontalOffset = languageLevel match {
      case Scala_2_11 => 7
      case _          => 0
    }

    (verticalOffset, horizontalOffset)
  }

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

  private case class InputOutputMappingItem(
    inputLinesInfo: InputLinesInfo,
    outputLinesInfo: OutputLinesInfo
  )

  /**
   * all lines are 0-based
   * contentStartLine <= firstElementLine <= lastElementLine <= contentEndLine
   * @example {{{
   *     // comment 1   // contentStartLine
   *     class A {      // firstElementLine
   *     }
   *
   *     // comment 2
   *     // comment 3
   *     object A {    // lastElementLine
   *     }             // contentEndLine
   * }}}
   */
  private case class InputLinesInfo(
    firstElementLine: Int,
    lastElementLine: Int,
    contentStartLine: Int,
    contentEndLine: Int,
    range: TextRange
  ) {
    def inputLinesCount: Int = (contentEndLine - firstElementLine) + 1
  }

  /**
   * @example {{{
   *  line input:       |  output
   *    0  42           |  val res0 = 42
   *    1               |                 \
   *    2               |                  | prefixNewLines= 3
   *    3  // comment   |                 /
   *    4  println(     |  23             | outputStartLine = 4
   *    5      23       |
   *    6  )            |                 | outputEndLine = 6
   * }}}
   */
  private final case class OutputLinesInfo(
    prefixNewLines: Int,
    outputStartLine: Int,
    outputEndLine: Int
  ) {
    def outputLinesCount: Int = outputEndLine - outputStartLine + 1
  }

  private final case class OutputInfo(
    linesInfo: OutputLinesInfo,
    actualOutputText: String
  )

  implicit class DocumentOps(private val document: Document) extends AnyVal {
    def lineNumberSafe(offset: Int): Option[Int] = {
      if (offset <= document.getTextLength) Some(document.getLineNumber(offset))
      else None
    }
  }
}
