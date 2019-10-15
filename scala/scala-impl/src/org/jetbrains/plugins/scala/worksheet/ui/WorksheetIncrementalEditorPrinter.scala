package org.jetbrains.plugins.scala
package worksheet
package ui

import java.util.regex.Pattern

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinterBase.FoldingOffsets
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetIncrementalEditorPrinter.QueuedPsi._

import scala.collection.mutable

class WorksheetIncrementalEditorPrinter(editor: Editor, viewer: Editor, file: ScalaFile) 
  extends WorksheetEditorPrinterBase(editor, viewer) {

  import WorksheetIncrementalEditorPrinter._
  import processor._

  private var lastProcessedLine: Option[Int] = None
  private var currentFile: ScalaFile = file
  
  private var hasErrors = false
  private var hasMessages = false

  private val outputBuffer = StringBuilder.newBuilder
  private val messagesBuffer = StringBuilder.newBuilder
  private val psiToProcess = mutable.Queue.empty[QueuedPsi]

  private val inputToOutputMapping = mutable.ListBuffer.empty[(Int, Int)]

  private def cleanViewerFrom(ln: Int): Unit = {
    if (ln == 0) {
      invokeLater {
        inWriteAction {
          simpleUpdate("", viewerDocument)
          cleanFoldings()
        }
      }
      
      return 
    }

    inWriteCommandAction {
      val start = viewerDocument.getLineStartOffset(ln)
      val end   = viewerDocument.getLineEndOffset(viewerDocument.getLineCount - 1)
      viewerDocument.deleteString(start, end)
    }
  }

  private def fetchNewPsi(): Unit = {
    lastProcessedLine match {
      case Some(lineNumber) =>
        val i = inputToOutputMapping.lastIndexWhere(_._1 == lineNumber)
        if (i == -1) {
          cleanViewerFrom(0)
        } else {
          val j = inputToOutputMapping.apply(i)._2
          
          if (j + 1 < viewerDocument.getLineCount)
            cleanViewerFrom(j + 1)
          if (inputToOutputMapping.length > j + 1)
            inputToOutputMapping.remove(j + 1, inputToOutputMapping.length - j - 1)
        }
      case _ =>
        cleanViewerFrom(0)
    }
    
    psiToProcess.clear()
    
    val buffer = mutable.ListBuffer[QueuedPsi]()
    val glue = new WorksheetPsiGlue(buffer)
    val iterator = new WorksheetInterpretExprsIterator(getScalaFile, Option(originalEditor), lastProcessedLine)
    iterator.collectAll(x => inReadAction(glue.processPsi(x)), None)
    
    psiToProcess.enqueue(buffer: _*)
  }

  private def clearMessages(): Unit = {
    hasMessages = false
    hasErrors = false
  }

  private def clearBuffer(): Unit = {
    outputBuffer.clear()
    messagesBuffer.clear()
  }
  
  override def getScalaFile: ScalaFile = currentFile

  override def processLine(line: String): Boolean = {
    if (!isInited) init()

    line.trim match {
      case REPL_START => 
        fetchNewPsi()
        if (lastProcessedLine.isEmpty)
          cleanFoldingsLater()
        clearMessages()
        clearBuffer()
        false
      case REPL_LAST_CHUNK_PROCESSED => 
        flushBuffer()
        refreshLastMarker()
        true
      case REPL_CHUNK_END => 
        if (hasErrors) refreshLastMarker() 
        flushBuffer()

        hasErrors
      case ReplMessage(info) => 
        messagesBuffer.append(info.msg).append("\n")
        hasMessages = true
        false
      case "" => //do nothing
        false
      case outputLine =>
        if (hasMessages) {
          messagesBuffer.append(line).append("\n")
          outputLine == "^" && { hasMessages = false; processMessage() }
        } else {
          outputBuffer.append(augmentLine(outputLine)).append("\n")
          false
        }
    }
  }

  override def flushBuffer(): Unit = {
    if (psiToProcess.isEmpty) return // empty output is possible see SCL-11720

    val outputText = outputBuffer.toString().trim
    outputBuffer.clear()
    
    val queuedPsi: QueuedPsi  = psiToProcess.dequeue()
    if (!queuedPsi.isValid) return //warning here?

    val linesCountOutput = countNewLines(outputText) + 1
    val linesCountInput  = countNewLines(queuedPsi.getText) + 1

    @inline
    /** @return lines index (0-based) */
    def originalLine(offset: Int): Int = originalDocument.getLineNumber(offset)

    val originalTextRange = queuedPsi.getWholeTextRange

    val processedStartLine    = originalLine(queuedPsi.getFirstProcessedOffset)
    val processedStartEndLine = originalLine(queuedPsi.getLastProcessedOffset)
    val processedEndLine      = originalLine(originalTextRange.getEndOffset)

    lastProcessedLine = Some(processedStartEndLine)

    WorksheetAutoRunner.getInstance(project).replExecuted(originalDocument, originalTextRange.getEndOffset)

    invokeLater {
      inWriteAction {
        val viewerDocumentLastLine = (viewerDocument.getLineCount - 1).max(0)

        // 1) append blank lines indentation to align input line from left editor with output line from right editor

        // a single visible line can actually contain many folded lines, so actual indexes can shift further
        // but the used does not see those folded lines so we need to extract folded lines
        val totalRegionsSpaces = foldGroup.regions.map(_.spaces).sum
        val viewerDocumentLastVisibleLine = (viewerDocumentLastLine - totalRegionsSpaces).max(0)

        val blankLinesBase = (processedStartLine - viewerDocumentLastVisibleLine).max(0)

        val prefix = buildNewLines(blankLinesBase)
        simpleAppend(prefix, viewerDocument)

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
          simpleAppend(prefix + chunkText, viewerDocument)
        }

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

  // Looks like we don't need any flushing here
  override def scheduleWorksheetUpdate(): Unit = {}

  /**  @return Number of the last processed line */
  def getLastProcessedLine: Option[Int] = lastProcessedLine

  def setLastProcessedLine(line: Option[Int]): Unit = lastProcessedLine = line

  def updateScalaFile(file: ScalaFile): Unit = currentFile = file

  private def augmentLine(inputLine: String): String = {
    val idx = inputLine.indexOf("$Lambda$")

    if (idx == -1) inputLine else {
      val prefix = inputLine.substring(0, Math.max(idx - 1, 0))
      val suffix = inputLine.substring(Math.min(inputLine.length, LAMBDA_LENGTH + idx + 1))
      prefix + "<function>" + suffix
    }
  }

  /**
    * @return true if error and should stop
    */
  private def processMessage(): Boolean = {
    if (psiToProcess.isEmpty) return false

    val currentPsi = psiToProcess.head
    val offset = currentPsi.getWholeTextRange.getStartOffset
    val str = messagesBuffer.toString().trim

    messagesBuffer.clear()

    val MessageInfo(msg, vertOffset, horizontalOffset, severity) = extractInfoFromAllText(str).getOrElse((str, 0, 0, WorksheetCompilerUtil.InfoSeverity))

    val position = {
      val p = inReadAction {
        originalEditor.offsetToLogicalPosition(offset)
      }
      new LogicalPosition(p.line + vertOffset, p.column + horizontalOffset)
    }


    val isFatal = severity.isFatal
    val messages = msg.split('\n').map(_.trim).filter(_.length > 0)
    val onError = if (isFatal) () => {originalEditor.getCaretModel moveToLogicalPosition position} else () => {}
    WorksheetCompilerUtil.showCompilationMessage(getScalaFile.getVirtualFile, position, messages, severity, onError)

    if (isFatal) {
      hasErrors = true
      psiToProcess.dequeue()
    }

    hasErrors
  }

  def extractInfoFromAllText(toMatch: String): Option[MessageInfo] = {
    val indexOfNl = toMatch.lastIndexOf('\n')
    if (indexOfNl == -1) return None

    val indexOfC = toMatch.lastIndexOf('^')
    val horOffset = if (indexOfC < indexOfNl) 0 else indexOfC - indexOfNl
    val allMessageStrings = toMatch.substring(0, indexOfNl)

    val matcher = CONSOLE_MESSAGE_PATTERN matcher allMessageStrings
    val (textWoConsoleLine, lineNumStr) = if (matcher.find()) (allMessageStrings.substring(matcher.end()), matcher.group(1)) else (allMessageStrings, "0")

    val (textWoSeverity, severity) = textWoConsoleLine match {
      case error if error.startsWith("error: ") =>
        (error.substring("error: ".length), WorksheetCompilerUtil.ErrorSeverity)
      case warning if warning.startsWith("warning: ") =>
        (warning.substring("warning: ".length), WorksheetCompilerUtil.WarningSeverity)
      case _ => return None
    }

    val (finalText, vertOffset) =
      splitLineNumberFromRepl(textWoSeverity).getOrElse {
        // we still have a fall back variant here as some errors aren't raised from the text of our input
        (textWoSeverity, Integer.parseInt(lineNumStr) - getConsoleHeaderLines(WorksheetCommonSettings(getScalaFile).getModuleFor))
      }

    Option(MessageInfo(finalText, vertOffset, horOffset, severity))
  }

  private def refreshLastMarker(): Unit =
    rehighlight(getScalaFile)
}

object WorksheetIncrementalEditorPrinter {

  import processor.WorksheetCompilerUtil

  val TECHNICAL_MESSAGE_START = "$$worksheet$$"

  private val REPL_START                = s"${TECHNICAL_MESSAGE_START}repl$$$$start$$$$"
  private val REPL_CHUNK_END            = s"${TECHNICAL_MESSAGE_START}repl$$$$chunk$$$$end$$$$"
  private val REPL_LAST_CHUNK_PROCESSED = s"${TECHNICAL_MESSAGE_START}repl$$$$last$$$$chunk$$$$processed$$$$"

  private val CONSOLE_ERROR_START = "<console>:"
  private val CONSOLE_MESSAGE_PATTERN = {
    val regex = "\\s*(\\d+)" + Pattern.quote(":") + "\\s*"
    Pattern.compile(regex)
  }

  private val LAMBDA_LENGTH = 32

  private def getConsoleHeaderLines(module: Module): Int = {
    import project._

    val isBefore = module.scalaSdk.exists { sdk =>
      sdk.properties.languageLevel match {
        case ScalaLanguageLevel.Scala_2_9 |
             ScalaLanguageLevel.Scala_2_10 => true
        case ScalaLanguageLevel.Scala_2_11 => sdk.compilerVersion.forall(!_.startsWith("2.11.8"))
        case _ => false
      }
    }

    if (isBefore) 7 else 11
  }

  def countNewLines(str: String): Int = StringUtil.countNewLines(str)

  def rehighlight(file: PsiFile): Unit = inReadAction {
    DaemonCodeAnalyzer.getInstance(file.getProject).restart(file)
  }

  case class MessageStart(msg: String)
  case class MessageInfo(text: String, verOffset: Int, horOffset: Int, severity: WorksheetCompilerUtil.CompilationMessageSeverity)

  object ReplMessage {
    def unapply(arg: String): Option[MessageStart] =
      if (arg startsWith CONSOLE_ERROR_START) Option(MessageStart(arg substring CONSOLE_ERROR_START.length)) else None
  }

  def splitLineNumberFromRepl(line: String): Option[(String, Int)] = {
    val i = line.lastIndexOf("//")
    if (i == -1) return None

    for {
      lineIdx <- line.substring(i + 2).trim.toIntOpt
    } yield (line.substring(0, i), lineIdx)
  }

  trait QueuedPsi {
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
      storeLineInfoRepl(StringUtil.splitByLines(psi.getText, false))

    protected def storeLineInfoRepl(lines: Array[String]): String = {
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
    private val (first, second) = if (isClazzFirst) (clazz, obj) else (obj, clazz)

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
}
