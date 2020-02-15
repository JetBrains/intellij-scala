package org.jetbrains.plugins.scala.worksheet.ui.printers

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter
import org.jetbrains.jps.incremental.scala.local.worksheet.PrintWriterReporter.MessageLineParsed
import org.jetbrains.plugins.scala.compiler.data.worksheet.ReplMessages
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.project
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.processor
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.CompilerMessagesConsumer
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterBase.FoldingOffsets
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterRepl.QueuedPsi.PrintChunk

import scala.collection.mutable

//noinspection HardCodedStringLiteral
final class WorksheetEditorPrinterRepl private[printers](
  editor: Editor,
  viewer: Editor,
  file: ScalaFile
) extends WorksheetEditorPrinterBase(editor, viewer) {

  import WorksheetEditorPrinterRepl._
  import ReplMessages._
  import processor._

  private var lastProcessedLine: Option[Int] = None
  private var currentFile: ScalaFile = file
  private var hasErrors = false

  /* we have to inject this interface because we have to restore the original error positions in worksheet editor
   * and for now this can only be done in this printer  */
  private var messagesConsumerOpt: Option[CompilerMessagesConsumer] = None

  private val outputBuffer = StringBuilder.newBuilder
  private val psiToProcess = mutable.Queue.empty[QueuedPsi]

  private val inputToOutputMapping = mutable.ListBuffer.empty[(Int, Int)]

  private def cleanViewerFromLine(lineIdx: Int): Unit = {
    if (lineIdx == 0) {
      invokeLater {
        inWriteAction {
          simpleUpdate("", viewerDocument)
          cleanFoldings()
        }
      }
    } else {
      inWriteCommandAction {
        val start = (viewerDocument.getLineStartOffset(lineIdx) - 1).max(0) // capture previous new line as well
        val end   = viewerDocument.getTextLength
        viewerDocument.deleteString(start, end)
      }
    }
  }

  private def fetchNewPsi(): Unit = {
    lastProcessedLine match {
      case Some(inputLine) =>
        inputToOutputMapping.lastWhere(_._1 == inputLine) match {
          case Some((_, outputLine)) =>
            if (outputLine + 1 < viewerDocument.getLineCount) {
              cleanViewerFromLine(outputLine)
            }
            if (inputToOutputMapping.length > outputLine + 1) {
              inputToOutputMapping.remove(outputLine + 1, inputToOutputMapping.length - outputLine - 1)
            }
          case _ =>
            cleanViewerFromLine(0)
        }
      case _ =>
        cleanViewerFromLine(0)
    }

    psiToProcess.clear()

    val glue = WorksheetPsiGlue()
    val iterator = new WorksheetInterpretExprsIterator(getScalaFile, Option(originalEditor), lastProcessedLine)
    iterator.collectAll(x => inReadAction(glue.processPsi(x)), None)
    val elements = glue.result

    psiToProcess.enqueue(elements: _*)
  }

  private def clearBuffer(): Unit =
    outputBuffer.clear()

  override def getScalaFile: ScalaFile = currentFile

  // FIXME: now all return boolean values are not processed anywhere and do not mean anything, remove or handle
  // FIXME: handle exceptions in process line
  override def processLine(line: String): Boolean = {
    if (!isInited) init()

    line.trim match {
      case ReplStart =>
        hasErrors = false
        fetchNewPsi()
        if (lastProcessedLine.isEmpty)
          cleanFoldingsLater()
        clearBuffer()
        false
      case ReplLastChunkProcessed =>
        flushBuffer()
        refreshLastMarker()
        true

      case ReplChunkStart =>
        false
      case ReplChunkEnd =>
        flushBuffer()
        false
      case ReplChunkCompilationError =>
        hasErrors = true
        flushBuffer()
        true

      case "" =>
        false
      case ReplMessage(line) =>
        messagesConsumerOpt.foreach(handleReplMessageLine(_, line))
        false
      case outputLine =>
        outputBuffer.append(augmentLine(outputLine)).append("\n")
        false
    }
  }

  override def flushBuffer(): Unit = {
    if (psiToProcess.isEmpty) return // empty output is possible see SCL-11720

    val outputText = outputBuffer.toString.trim
    outputBuffer.clear()

    val queuedPsi: QueuedPsi = psiToProcess.dequeue()
    if (!queuedPsi.isValid) return //warning here?

    val linesCountOutput = countNewLines(outputText) + 1
    val linesCountInput  = countNewLines(queuedPsi.getText) + 1

    @inline
    /** @return lines index (0-based) */
    def originalLine(offset: Int): Int = originalDocument.getLineNumber(offset)

    val originalTextRange = inReadAction(queuedPsi.getWholeTextRange)

    val processedStartLine    = originalLine(queuedPsi.getFirstProcessedOffset)
    val processedStartEndLine = originalLine(queuedPsi.getLastProcessedOffset)
    val processedEndLine      = originalLine(originalTextRange.getEndOffset)

    if (!hasErrors)
      lastProcessedLine = Some(processedStartEndLine)

    WorksheetAutoRunner.getInstance(project).replExecuted(originalDocument, originalTextRange.getEndOffset)

    invokeLater {
      inWriteAction {
        val viewerDocumentLastLine = (viewerDocument.getLineCount - 1).max(0)

        // 1) append blank lines indentation to align input line from left editor with output line from right editor

        // a single visible line can actually contain many folded lines, so actual indexes can shift further
        // but the used does not see those folded lines so we need to extract folded lines
        val numberOfFoldedLines = foldGroup.foldedLines
        val viewerDocumentLastVisibleLine = (viewerDocumentLastLine - numberOfFoldedLines).max(0)

        val blankLinesBase = (processedStartLine - viewerDocumentLastVisibleLine).max(0)

        val prefix = buildNewLines(blankLinesBase)
        val currentOutput = new mutable.StringBuilder(prefix.length)
        currentOutput.append(prefix)

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

        val inputLine  = processedStartEndLine
        val outputLine = viewerDocumentLastLine + blankLinesBase + linesCountOutput  + blankLinesFromOutput
        inputToOutputMapping.append((inputLine, outputLine))

        saveEvaluationResult(viewerDocument.getText)

        if (linesCountOutput > linesCountInput) {
          val lineCount = viewerDocument.getLineCount

          val outputStartLine = viewerDocumentLastLine + blankLinesBase
          val outputEndOffset = viewerDocument.getLineEndOffset(lineCount - 1)

          val foldings = FoldingOffsets(
            outputStartLine,
            outputEndOffset,
            linesCountInput,
            processedEndLine
          )
          updateFoldings(Seq(foldings))
        }
      }
    }
  }

  def close(): Unit = {}

  // Looks like we don't need any flushing here
  override def scheduleWorksheetUpdate(): Unit = {}

  /**  @return Number of the last processed line */
  def getLastProcessedLine: Option[Int] = lastProcessedLine

  def setLastProcessedLine(line: Option[Int]): Unit = lastProcessedLine = line

  def updateScalaFile(file: ScalaFile): Unit = currentFile = file

  def updateMessagesConsumer(consumer: CompilerMessagesConsumer): Unit = messagesConsumerOpt = Some(consumer)

  private def augmentLine(inputLine: String): String = {
    val idx = inputLine.indexOf("$Lambda$")

    if (idx == -1) inputLine else {
      val prefix = inputLine.substring(0, Math.max(idx - 1, 0))
      val suffix = inputLine.substring(Math.min(inputLine.length, LAMBDA_LENGTH + idx + 1))
      prefix + "<function>" + suffix
    }
  }

  private def handleReplMessageLine(messagesConsumer: CompilerMessagesConsumer, messageLine: String): Unit = {
    val currentPsi = psiToProcess.headOption match {
      case Some(value) => value
      case None        => return
    }

    val ReplMessageInfo(message, lineContent, lineOffset, columnOffset, severity) =
      extractReplMessage(messageLine)
        .getOrElse(ReplMessageInfo(messageLine, "", 0, 0, CompilerMessageCategory.INFORMATION))

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
    val compilerMessage = new CompilerMessageImpl(
      project,
      severity,
      messageLines.mkString("\n"),
      file.getVirtualFile,
      messagePosition.line + 1, // compiler messages positions are 1-based
      messagePosition.column + 1,
      null
    )
    messagesConsumer.message(compilerMessage)
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

  private def refreshLastMarker(): Unit =
    rehighlight(getScalaFile)
}

object WorksheetEditorPrinterRepl {

  private val CONSOLE_REPORT_PREFIX = PrintWriterReporter.IJReportPrefix

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

  def rehighlight(file: PsiFile): Unit = inReadAction {
    DaemonCodeAnalyzer.getInstance(file.getProject).restart(file)
  }

  private case class ReplMessageInfo(text: String,
                                     lineContent: String,
                                     lineOffset: Int,
                                     columnOffset: Int,
                                     messageCategory: CompilerMessageCategory)

  object ReplMessage {

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

  sealed trait QueuedPsi {
    /**
     * @return underlying psi(-s) is valid
     */
    final def isValid: Boolean = inReadAction {
      isValidImpl
    }

    protected def isValidImpl: Boolean

    /**
     * @return the whole corresponding input text
     */
    final def getText: String = inReadAction {
      getTextImpl
    }

    protected def getTextImpl: String

    /**
     * @return input text range
     */
    def getWholeTextRange: TextRange

    /**
     * @param output the whole trimmed output from the interpreter
     */
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
  case class ClassObjectPsi(clazz: ScTypeDefinition, obj: ScObject, mid: String, isClazzFirst: Boolean) extends QueuedPsi {
    val (first, second) = if (isClazzFirst) (clazz, obj) else (obj, clazz)

    override protected def isValidImpl: Boolean = clazz.isValid && obj.isValid

    override protected def getTextImpl: String = getPsiTextWithCommentLine(first) + mid + getPsiTextWithCommentLine(second)

    override def getWholeTextRange: TextRange = new TextRange(first.startOffset, second.endOffset)

    override def getPrintChunks(output: String): Seq[PrintChunk] = {
      //we assume output is class A defined \n class B defined
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
}
