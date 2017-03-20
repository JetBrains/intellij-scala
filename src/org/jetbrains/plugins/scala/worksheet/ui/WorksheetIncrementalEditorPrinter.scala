package org.jetbrains.plugins.scala.worksheet.ui

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

  private val outputBuffer = new StringBuilder
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
  
  private def clearErrors() {
    hasErrors = false
  }
  
  private def clearBuffer() {
    outputBuffer.clear()
  }
  
  override def getScalaFile: ScalaFile = currentFile

  override def processLine(line: String): Boolean = {
    line.trim match {
      case REPL_START => 
        fetchNewPsi()
        if (lastProcessed.isEmpty) cleanFoldings()
        clearErrors()
        clearBuffer()
        false
      case REPL_LAST_CHUNK_PROCESSED => 
        flushBuffer()
        refreshLastMarker()
        true
      case REPL_CHUNK_END => 
        if (hasErrors) {
          refreshLastMarker()
          processError()
        } else flushBuffer()
        hasErrors
      case ReplError(info) => 
        outputBuffer.append(info.msg).append("\n")
        hasErrors = true
        false
      case "" => //do nothing
        false
      case outputLine => 
        outputBuffer.append(if (hasErrors) line else augmentLine(outputLine))
        if (!outputLine.endsWith("\n")) outputBuffer.append("\n")
        false
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
    val processedStartLine = {
      val actualStart = currentPsi.getFirstChild match {
        case comment: PsiComment => 
          var c = comment.getNextSibling
          while (c.isInstanceOf[PsiComment] || c.isInstanceOf[PsiWhiteSpace]) c = c.getNextSibling
          
          if (c != null) c else currentPsi
        case _ => currentPsi
      }

      originalDocument getLineNumber actualStart.getTextRange.getStartOffset
    }
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
    
    if (idx == -1) inputLine else 
      inputLine.substring(0, Math.max(idx - 1, 0)) + 
        "<function>" + 
        inputLine.substring(Math.min(inputLine.length, LAMBDA_LENGTH + idx + 1), inputLine.length)
  }
  
  private def processError() {
    val currentPsi = psiToProcess.dequeue()
    val offset = currentPsi.getTextOffset
    val str = outputBuffer.toString().trim

    outputBuffer.clear()

    val (msg, horizontalOffset) = ReplError.extractInfoFromAllText(str).getOrElse((str, 0))
    val position = originalEditor.offsetToLogicalPosition(offset + horizontalOffset)
    
    WorksheetCompiler.showCompilationError(getScalaFile.getVirtualFile, position.line, position.column, project, 
      () => {originalEditor.getCaretModel moveToLogicalPosition position}, msg.split('\n'))
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
  
  private val LAMBDA_LENGTH = 32
  
  case class ErrorInfo(msg: String)
  
  object ReplError {
    def unapply(arg: String): Option[ErrorInfo] = {
      if (arg startsWith CONSOLE_ERROR_START) {
        val i = arg.indexOf("error: ")
        if (i != -1) Option(ErrorInfo(arg.substring(i))) else None 
      } else None
    } 
    
    def extractInfoFromAllText(text: String): Option[(String, Int)] = {
      val i = text.indexOf("error: ")
      val j = text.lastIndexOf('\n')
      if (i == -1 || j == -1) None else {
        Option((text.substring(i, j), text.length - 1 - j - CONSOLE_ERROR_START.length))
      }
    }
  }
}
