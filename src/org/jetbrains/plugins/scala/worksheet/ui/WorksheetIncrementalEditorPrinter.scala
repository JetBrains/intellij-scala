package org.jetbrains.plugins.scala.worksheet.ui

import java.util.regex.Pattern

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.implementation.iterator.PrevSiblignsIterator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.processor.{WorksheetCompiler, WorksheetInterpretExprsIterator}

import scala.collection.mutable

/**
  * User: Dmitry.Naydanov
  * Date: 07.02.17.
  */
class WorksheetIncrementalEditorPrinter(editor: Editor, viewer: Editor, file: ScalaFile) 
  extends WorksheetEditorPrinterBase(editor, viewer) {
  import WorksheetIncrementalEditorPrinter._
  
  private var lastProcessed: Option[Int] = None
  private var currentFile = file
  
  private var hasErrors = false
  private var hasMessages = false

  private val outputBuffer = new StringBuilder
  private val messagesBuffer = new StringBuilder
  private val psiToProcess = mutable.Queue[PsiElement]()
  
  private val inputToOutputMapping = mutable.ListBuffer[(Int, Int)]()

  private def cleanViewerFrom(ln: Int) {
    if (ln == 0) {
      extensions.invokeLater {
        extensions.inWriteAction {
          simpleUpdate("", viewerDocument)
        }
      }
      
      return 
    }
    
    WriteCommandAction.runWriteCommandAction(project, new Runnable {
      override def run(): Unit = 
        viewerDocument.deleteString (
          viewerDocument.getLineStartOffset(ln), 
          viewerDocument.getLineEndOffset(viewerDocument.getLineCount)
        )
    })
    
  }
  
  private def fetchNewPsi() {
    lastProcessed match {
      case Some(lineNumber) =>
        val i = inputToOutputMapping.lastIndexWhere(_._1 == lineNumber)
        if (i == -1) cleanViewerFrom(0) else {
          val j = inputToOutputMapping.apply(i)._2
          
          if (j + 1 < viewerDocument.getLineCount) cleanViewerFrom(j + 1)
          if (inputToOutputMapping.length > j + 1) inputToOutputMapping.remove(j + 1, inputToOutputMapping.length - j - 1)
        }
      case _ => cleanViewerFrom(0)
    }
    
    psiToProcess.clear()
    
    new WorksheetInterpretExprsIterator(getScalaFile, Option(originalEditor), lastProcessed).collectAll(
      a => psiToProcess.enqueue(a), None
    )
  }
  
  private def countNewLines(str: String) = StringUtil countNewLines str
  
  private def clearMessages() {
    hasMessages = false
    hasErrors = false
  }
  
  private def clearBuffer() {
    outputBuffer.clear()
    messagesBuffer.clear()
  }
  
  override def getScalaFile: ScalaFile = currentFile

  override def processLine(line: String): Boolean = {
    line.trim match {
      case REPL_START => 
        fetchNewPsi()
        if (lastProcessed.isEmpty) cleanFoldings()
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
          outputLine == "^" && {hasMessages = false; processMessage()}
        } else {
          outputBuffer.append(augmentLine(outputLine)).append("\n")
          false
        }
    }
  }

  override def flushBuffer(): Unit = {
    if (outputBuffer.isEmpty || psiToProcess.isEmpty) return 

    val str = outputBuffer.toString().trim
    outputBuffer.clear()
    
    val currentPsi = psiToProcess.dequeue()
    val isValid = extensions.inReadAction(currentPsi.isValid)
    if (!isValid) return //warning here?
    
    val linesOutput = countNewLines(str) + 1 
    val psiText = extensions.inReadAction {
      currentPsi.getText
    }
    val linesInput = countNewLines(psiText) + 1

    val originalTextRange = currentPsi.getTextRange
    val processedStartLine = originalDocument getLineNumber originalTextRange.getStartOffset
    val processedEndLine = originalDocument getLineNumber originalTextRange.getEndOffset
    
    val firstOffsetFix = if (lastProcessed.isEmpty) 0 else 1
    lastProcessed = Some(processedStartLine)
    WorksheetAutoRunner.getInstance(project).replExecuted(originalDocument, originalTextRange.getEndOffset)
  
    
    def countLinesWoCode(nextFrom: PsiElement): Int = {
      val it = new PrevSiblignsIterator(nextFrom)
      var counter = 1
      
      while (it.hasNext) it.next() match {
        case ws: PsiWhiteSpace => counter += countNewLines(ws.getText)
        case com: PsiComment => counter += countNewLines(com.getText)
        case null => return counter - 1
        case _ => return counter - 2
      }
      
      counter - 1
    }

    extensions.invokeLater {
      WriteCommandAction.runWriteCommandAction(project, new Runnable {
        override def run(): Unit = {
          val diff = Math.max(processedStartLine - viewerDocument.getLineCount - 1, 0) + countLinesWoCode(currentPsi)
          
          inputToOutputMapping.append((processedStartLine, linesOutput + diff - 1 + viewerDocument.getLineCount))

          val oldLinesCount = viewerDocument.getLineCount
          val prefix = getNewLines(diff + firstOffsetFix)
          simpleAppend(prefix + str, viewerDocument)
          
          saveEvaluationResult(viewerDocument.getText)

          if (linesOutput > linesInput) {
            val lineCount = viewerDocument.getLineCount
            updateFoldings(Seq((oldLinesCount + diff + firstOffsetFix - 1, viewerDocument.getLineEndOffset(lineCount - 1), linesInput, processedEndLine)))
          }
        }
      })
    }
  }

  /*
  Looks like we don't need any flushing here
   */
  override def scheduleWorksheetUpdate(): Unit = {}

  /**
    * 
    * @return Number of the last processed line
    */
  def getLastProcessedLine: Option[Int] = lastProcessed
  
  def setLastProcessedLine(i: Option[Int]) {
    lastProcessed = i 
  }
  
  def updateScalaFile(file: ScalaFile) {
    currentFile = file
  }
  
  private def augmentLine(inputLine: String): String = {
    val idx = inputLine.indexOf("$Lambda$")
    
    if (idx == -1) inputLine else {
      val until = Math.min(inputLine.length, LAMBDA_LENGTH + idx + 1)
      inputLine.substring(idx, until) + "<function>"
    }
  }
  
  private def augmentLine(inputLine: String): String = {
    val idx = inputLine.indexOf("$Lambda$")
    
    if (idx == -1) inputLine else {
      val until = Math.min(inputLine.length, LAMBDA_LENGTH + idx + 1)
      inputLine.substring(idx, until) + "<function>"
    }
  }

  /**
    * 
    * @return true if error and should stop
    */
  private def processMessage(): Boolean = {
    if (psiToProcess.isEmpty) return false
    
    val currentPsi = psiToProcess.head
    val offset = currentPsi.getTextOffset
    val str = messagesBuffer.toString().trim

    messagesBuffer.clear()

    val MessageInfo(msg, horizontalOffset, severity) = ReplMessage.extractInfoFromAllText(str).getOrElse((str, 0))
    val position = extensions.inReadAction { originalEditor.offsetToLogicalPosition(offset + horizontalOffset) }

    val isFatal = severity.isFatal
    val onError = if (isFatal) () => {originalEditor.getCaretModel moveToLogicalPosition position} else () => {}
    
    WorksheetCompiler.showCompilationMessage(
      getScalaFile.getVirtualFile, severity, position.line, position.column, project, onError, msg.split('\n').map(_.trim).filter(_.length > 0))
    
    if (isFatal) {
      hasErrors = true
      psiToProcess.dequeue()
    }
    
    hasErrors
  }
  
  private def refreshLastMarker() {
    DaemonCodeAnalyzer.getInstance(project).restart(getScalaFile)
  }
}

object WorksheetIncrementalEditorPrinter {
  private val REPL_START = "$$worksheet$$repl$$start$$"
  private val REPL_CHUNK_END = "$$worksheet$$repl$$chunk$$end$$"
  private val REPL_LAST_CHUNK_PROCESSED = "$$worksheet$$repl$$last$$chunk$$processed$$"
  
  private val CONSOLE_ERROR_START = "<console>:"
  private val CONSOLE_MESSAGE_PATTERN = {
    val regex = Pattern.quote("<console>:") + "\\s*\\d+" + Pattern.quote(":") + "\\s*"
    Pattern.compile(regex)
  }
  
  private val LAMBDA_LENGTH = 32
  
  case class MessageStart(msg: String)
  case class MessageInfo(text: String, horOffset: Int, severity: WorksheetCompiler.CompilationMessageSeverity)
  
  object ReplMessage {
    def unapply(arg: String): Option[MessageStart] = {
      if (arg startsWith CONSOLE_ERROR_START) {
        val matcher = CONSOLE_MESSAGE_PATTERN.matcher(arg)
        if (!matcher.find()) return None
        
        Option(MessageStart(arg substring matcher.end())) 
      } else None
    } 
    
    def extractInfoFromAllText(text: String): Option[MessageInfo] = {
      val (nt, severity) = text match {
        case error if error.startsWith("error: ") =>
          (error.substring("error: ".length), WorksheetCompiler.ErrorSeverity)
        case warning if warning.startsWith("warning: ") =>
          (warning.substring("warning: ".length), WorksheetCompiler.WarningSeverity)
        case _ => return None
      }

      val j = nt.lastIndexOf('\n')
      if (j == -1) return None

      Option(MessageInfo(nt.substring(0, j), nt.length - 1 - j - CONSOLE_ERROR_START.length, severity))
    }
  }
}
