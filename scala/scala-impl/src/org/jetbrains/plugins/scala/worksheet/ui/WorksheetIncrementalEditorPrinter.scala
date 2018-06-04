package org.jetbrains.plugins.scala.worksheet.ui

import java.util.regex.Pattern

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.implementation.iterator.PrevSiblignsIterator
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.worksheet.GotoOriginalHandlerUtil
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner
import org.jetbrains.plugins.scala.worksheet.processor.{WorksheetCompiler, WorksheetInterpretExprsIterator, WorksheetPsiGlue}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
  private val psiToProcess = mutable.Queue[QueuedPsi]()
  
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
          viewerDocument.getLineEndOffset(viewerDocument.getLineCount - 1)
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
    
    val buffer = mutable.ListBuffer[QueuedPsi]()
    val glue = new WorksheetPsiGlue(buffer)
    new WorksheetInterpretExprsIterator(getScalaFile, Option(originalEditor), lastProcessed).collectAll(
      glue.processPsi, None
    )
    
    psiToProcess.enqueue(buffer: _*)
  }
  
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
    if (!isInited) init()
    
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
    if (psiToProcess.isEmpty) return // empty output is possible see SCL-11720

    val str = outputBuffer.toString().trim
    outputBuffer.clear()
    
    val queuedPsi: QueuedPsi  = psiToProcess.dequeue()
    if (!queuedPsi.isValid) return //warning here?
    
    val linesOutput = countNewLines(str) + 1 
    val linesInput = countNewLines(queuedPsi.getText) + 1
    
    @inline def originalLn(offset: Int) = originalDocument getLineNumber offset

    val originalTextRange = queuedPsi.getWholeTextRange
    val processedStartLine = originalLn(queuedPsi.getFirstProcessedOffset)
    val processedStartEndLine = originalLn(queuedPsi.getLastProcessedOffset)
    val processedEndLine = originalLn(originalTextRange.getEndOffset)
    
    val firstOffsetFix = if (lastProcessed.isEmpty) 0 else 1
    lastProcessed = Some(processedStartEndLine)
    WorksheetAutoRunner.getInstance(project).replExecuted(originalDocument, originalTextRange.getEndOffset)
  
    extensions.invokeLater {
      WriteCommandAction.runWriteCommandAction(project, new Runnable {
        override def run(): Unit = {
          val oldLinesCount = viewerDocument.getLineCount

          val baseDiff = Math.max(processedStartLine - viewerDocument.getLineCount - 1, 0) + queuedPsi.getBaseDiff

          val prefix = getNewLines(baseDiff + firstOffsetFix)
          simpleAppend(prefix, viewerDocument)
          var addedDiff = 0
          
          queuedPsi.getPrintStartOffset(str) foreach {
            case (absoluteOffset, relativeOffset, outputChunk) => 
              val df = originalLn(absoluteOffset) - originalLn(absoluteOffset - relativeOffset)
              addedDiff += df
              val currentPrefix = getNewLines(df)
              simpleAppend(currentPrefix + outputChunk, viewerDocument)
          }
          
          inputToOutputMapping.append((processedStartEndLine, linesOutput + baseDiff + addedDiff - 1 + viewerDocument.getLineCount))
          
          saveEvaluationResult(viewerDocument.getText)

          if (linesOutput > linesInput) {
            val lineCount = viewerDocument.getLineCount
            updateFoldings(Seq((oldLinesCount + baseDiff + firstOffsetFix - 1, viewerDocument.getLineEndOffset(lineCount - 1), linesInput, processedEndLine)))
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

  /**
    * 
    * @return true if error and should stop
    */
  private def processMessage(): Boolean = {
    if (psiToProcess.isEmpty) return false
    
    val currentPsi = psiToProcess.head
    val offset = currentPsi.getWholeTextRange.getStartOffset
    val str = messagesBuffer.toString().trim

    messagesBuffer.clear()

    val MessageInfo(msg, vertOffset, horizontalOffset, severity) = extractInfoFromAllText(str).getOrElse((str, 0, 0, WorksheetCompiler.InfoSeverity))
    
    val position = {
      val p = extensions.inReadAction { originalEditor.offsetToLogicalPosition(offset) }
      new LogicalPosition(p.line + vertOffset, p.column + horizontalOffset)
    }
    
    
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
        (error.substring("error: ".length), WorksheetCompiler.ErrorSeverity)
      case warning if warning.startsWith("warning: ") =>
        (warning.substring("warning: ".length), WorksheetCompiler.WarningSeverity)
      case _ => return None
    }

    val (finalText, vertOffset) = {
      splitLineNumberFromRepl(textWoSeverity) match {
        case Some(a) => a
        case _ => // we still have a fall back variant here as some errors aren't raised from the text of our input
          (textWoSeverity, Integer.parseInt(lineNumStr) - getConsoleHeaderLines(WorksheetCommonSettings.getInstance(getScalaFile).getModuleFor))
      }
    }

    Option(MessageInfo(finalText, vertOffset, horOffset, severity))
  }
  
  private def refreshLastMarker() {
    rehighlight(getScalaFile)
  }
}

object WorksheetIncrementalEditorPrinter {
  private val REPL_START = "$$worksheet$$repl$$start$$"
  private val REPL_CHUNK_END = "$$worksheet$$repl$$chunk$$end$$"
  private val REPL_LAST_CHUNK_PROCESSED = "$$worksheet$$repl$$last$$chunk$$processed$$"
  
  private val CONSOLE_ERROR_START = "<console>:"
  private val CONSOLE_MESSAGE_PATTERN = {
    val regex = "\\s*(\\d+)" + Pattern.quote(":") + "\\s*"
    Pattern.compile(regex)
  }
  
  private val LAMBDA_LENGTH = 32
  
  private def getConsoleHeaderLines(module: Module): Int = {
    import org.jetbrains.plugins.scala.project.ScalaLanguageLevel._
    import org.jetbrains.plugins.scala.project._
    
    val before = 7
    val after = 11
    
    module.scalaSdk.map(
      sdk => (sdk.compilerVersion, sdk.languageLevel)
    ) map {
      case (v, l) => l match {
        case Scala_2_8 | Scala_2_9 | Scala_2_10 => before
        case Scala_2_11 => if (v.exists(_ startsWith "2.11.8")) after else before
        case _ => after
      }
    } getOrElse after
  }
  
  def executeResNDeclarations(processor: PsiScopeProcessor, file: FileDeclarationsHolder, state: ResolveState): Boolean = {
    var ind = 0

    file.getChildren foreach {
      case expr: ScExpression =>
        val text = expr.getText

        if (!text.contains(ScalaCompletionUtil.DUMMY_IDENTIFIER)) {
          val name = s"res$ind"
          val inds = ArrayBuffer[Int]()
          val m = name.r.pattern.matcher(text)
          while (m.find()) {
            inds += m.start()
          }

          val skip = inds exists {
            idx => file.findElementAt(expr.getTextRange.getStartOffset + idx + 1) match {
              case psi: PsiElement if psi.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
                true
              case _ => false
            }
          }

          if (!skip) ScalaPsiElementFactory.createDefinitionWithContext(s" val res$ind = $text", file, expr) match {
            case patternDef: ScPatternDefinition =>
              patternDef.declaredElements foreach {
                declared =>
                  GotoOriginalHandlerUtil.storeNonModifiablePsi(declared, expr)
                  if (!processor.execute(declared, state)) return false
              }
            case _ =>
          }
          ind += 1
        }
      case _ =>
    }
    
    true
  }

  def countNewLines(str: String): Int = StringUtil countNewLines str
  
  def rehighlight(file: PsiFile) {
    DaemonCodeAnalyzer.getInstance(file.getProject).restart(file)
  }
  
  case class MessageStart(msg: String)
  case class MessageInfo(text: String, verOffset: Int, horOffset: Int, severity: WorksheetCompiler.CompilationMessageSeverity)
  
  object ReplMessage {
    def unapply(arg: String): Option[MessageStart] = 
      if (arg startsWith CONSOLE_ERROR_START) Option(MessageStart(arg substring CONSOLE_ERROR_START.length)) else None 
  }

  def splitLineNumberFromRepl(line: String): Option[(String, Int)] = {
    val i = line.lastIndexOf("//")
    if (i == -1) return None

    try {
      Option((line.substring(0, i), Integer parseInt line.substring(i + 2).trim))
    } catch {
      case _: NumberFormatException => None
    }
  }


  
  trait QueuedPsi {
    /**
      * @return underlying psi(-s) is valid
      */
    final def isValid: Boolean = extensions.inReadAction{ isValidImpl }
    
    protected def isValidImpl: Boolean
    
    /**
      * @return the whole corresponding input text
      */
    final def getText: String = extensions.inReadAction{ getTextImpl }

    protected def getTextImpl: String
    
    /**
      * @return input text range
      */
    def getWholeTextRange: TextRange

    /**
      * @param output the whole trimmed output from the interpreter
      * @return sequence of splited output (absolute offset in input document, offset from the end of previous output token or from rel zero, output token text)  
      */
    def getPrintStartOffset(output: String): Seq[(Int, Int, String)]
    
    def getFirstProcessedOffset: Int
    
    def getLastProcessedOffset: Int = getFirstProcessedOffset
    
    def getBaseDiff: Int
    
    protected def computeStartPsi(psi: PsiElement): PsiElement = {
      val actualStart = psi.getFirstChild match {
        case comment: PsiComment =>
          var c = comment.getNextSibling
          while (c.isInstanceOf[PsiComment] || c.isInstanceOf[PsiWhiteSpace]) c = c.getNextSibling

          if (c != null) c else psi
        case _ => psi
      }

      actualStart
    }
    
    protected def psiToStartOffset(psi: PsiElement): Int = psi.getTextRange.getStartOffset
    
    protected def countLinesWoCode(nextFrom: PsiElement): Int = {
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
    
    protected def startPsiOffset(psi: PsiElement): Int = psiToStartOffset(computeStartPsi(psi))

    protected def getPsiTextWithCommentLine(psi: PsiElement): String =
      storeLineInfoRepl(StringUtil.splitByLines(psi.getText, false))
    
    protected def storeLineInfoRepl(lines: Array[String]): String = {
      lines.zipWithIndex.map {
        case (line, index) => line + s" //$index\n"
      }.mkString("")
    }
  }
  
  case class SingleQueuedPsi(psi: PsiElement) extends QueuedPsi {
    override protected def isValidImpl: Boolean = psi.isValid

    override protected def getTextImpl: String = getPsiTextWithCommentLine(psi)

    override def getWholeTextRange: TextRange = psi.getTextRange

    override def getPrintStartOffset(output: String): Seq[(Int, Int, String)] = Seq((startPsiOffset(psi), 0, output))

    override def getBaseDiff: Int = countLinesWoCode(psi)

    override def getFirstProcessedOffset: Int = startPsiOffset(psi)
  }
  
  case class ClassObjectPsi(clazz: ScClass, obj: ScObject, mid: String, isClazzFirst: Boolean) extends QueuedPsi {
    private val (first, second) = if (isClazzFirst) (clazz, obj) else (obj, clazz)
    
    override protected def isValidImpl: Boolean = clazz.isValid && obj.isValid

    override protected def getTextImpl: String =  getPsiTextWithCommentLine(first) + mid + getPsiTextWithCommentLine(second)   

    override def getWholeTextRange: TextRange = new TextRange(psiToStartOffset(first), second.getTextRange.getEndOffset) 

    override def getPrintStartOffset(output: String): Seq[(Int, Int, String)] = {
      //we assume output is class A defined \n class B defined
      val i = output.indexOf('\n')
      val (one, two) = if (i == -1) (output, "") else output.splitAt(i)
      
      val firstOffset = startPsiOffset(first)
      val secondOffset = startPsiOffset(second)
      
      Seq((firstOffset, 0, one), (secondOffset, secondOffset - firstOffset, two.trim))
    }

    override def getBaseDiff: Int = countLinesWoCode(first)

    override def getFirstProcessedOffset: Int = startPsiOffset(first)

    override def getLastProcessedOffset: Int = startPsiOffset(second)
  }
}
