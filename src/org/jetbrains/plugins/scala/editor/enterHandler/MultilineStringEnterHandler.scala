package org.jetbrains.plugins.scala
package editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import lang.psi.api.ScalaFile
import lang.lexer.ScalaTokenTypes
import lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.openapi.util.text.StringUtil
import lang.psi.api.base.{ScReferenceElement, ScLiteral}
import com.intellij.psi.{PsiElement, PsiDocumentManager, PsiFile}
import com.intellij.psi.codeStyle.{CodeStyleSettingsManager, CodeStyleManager}
import lang.psi.api.expr._
import collection.mutable.ArrayBuffer
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.codeInsight.CodeInsightSettings

/**
 * User: Dmitry Naydanov
 * Date: 2/27/12
 */

class MultilineStringEnterHandler extends EnterHandlerDelegateAdapter {
  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer], caretAdvance: Ref[Integer], 
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    val document = editor.getDocument
    val text = document.getCharsSequence
    val caretOffset = caretOffsetRef.get.intValue

    if (caretOffset == 0 || caretOffset >= text.length()) return Result.Continue
    
    val ch1 = text.charAt(caretOffset - 1)
    val ch2 = text.charAt(caretOffset)

    if ((ch1 != '(' || ch2 != ')')&&(ch1 != '{' || ch2 != '}') || !CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER) 
      return Result.Continue
    
    val element = file findElementAt caretOffset
    if (element == null || element.getNode.getElementType != ScalaTokenTypes.tMULTILINE_STRING)
      return Result.Continue
    
    originalHandler.execute(editor, dataContext)
    Result.DefaultForceIndent
  }

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    import MultilineStringEnterHandler.{multilineQuotes, multilineQuotesLength}

    if (!file.isInstanceOf[ScalaFile]) return Result.Continue

    val caretModel = editor.getCaretModel
    val offset = caretModel.getOffset
    val project = file.getProject
    val document = editor.getDocument
    val element = file.findElementAt(offset)
    
    if (element == null || element.getNode.getElementType != ScalaTokenTypes.tMULTILINE_STRING)
      return Result.Continue

    val settings = CodeStyleSettingsManager.getInstance(element.getProject).getCurrentSettings
    val scalaSettings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
    
    val marginChar = MultilineStringEnterHandler getMarginChar element
    val useTabs = settings useTabCharacter ScalaFileType.SCALA_FILE_TYPE
    val tabSize = settings getTabSize ScalaFileType.SCALA_FILE_TYPE
    val myIndentSize = scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT

    val elementOffset = element.getTextOffset
    if (scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_NONE ||
      offset - elementOffset < 3) return Result.Continue


    @inline def getLineByNumber(number: Int): String =
      document.getText(new TextRange(document.getLineStartOffset(number), document.getLineEndOffset(number)))
    
    @inline def getSpaces(count: Int) = StringUtil.repeat(" ", count)
    
    @inline def getSmartSpaces(count: Int) = if (useTabs) {
      StringUtil.repeat("\t", count/tabSize) + StringUtil.repeat(" ", count%tabSize)
    } else {
      StringUtil.repeat(" ", count)
    }
    
    @inline def getSmartLength(line: String) = if (useTabs) line.length + line.count(_ == '\t')*(tabSize - 1) else line.length 
    
    @inline def insertNewLine(nlOffset: Int) {
      document.insertString(nlOffset, "\n")
    }

    @inline def selectBySettings(ifIndent: => Unit)(ifAll: => Unit) {
      scalaSettings.MULTILINE_STRING_SUPORT match {
        case ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT => ifIndent
        case ScalaCodeStyleSettings.MULTILINE_STRING_ALL => ifAll
      }
    }
    
    def enterInsideBraces(nextLine: String, prevLine: String) {
      
    }
    
    def moveCaret(verticalShift: Int, horizontalShift: Int) {
      val visualPosition = caretModel.getVisualPosition
      
      val newLineNumber = visualPosition.getLine + verticalShift
      
      if (newLineNumber > document.getLineCount) return 
      
      val newHorPosition = visualPosition.getColumn + horizontalShift
      val newLineEndOffset = document.getLineEndOffset(newLineNumber) 
      val newLineLength = getSmartLength(getLineByNumber(newLineNumber))
      
      if (newHorPosition > newLineLength) 
        document.insertString(newLineEndOffset, getSpaces(newHorPosition - newLineLength))
      
      caretModel.moveCaretRelatively(horizontalShift, verticalShift, false, false, false)
    }

    extensions inWriteAction {
      var caretShiftHor = 0
      var caretShiftVert = 0

      lazy val prevLineNumber = document.getLineNumber(offset - 1) - 1
      assert(prevLineNumber >= 0)
      lazy val prevLine = getLineByNumber(prevLineNumber)
      lazy val currentLine = getLineByNumber(prevLineNumber + 1)
      lazy val nextLine = if (document.getLineCount > prevLineNumber + 2) getLineByNumber(prevLineNumber + 2) else ""

      @inline def prefixLength(line: String) = if (useTabs) {
        val tabsCount = line prefixLength (_ == '\t')
        tabsCount*tabSize + line.substring(tabsCount).prefixLength(_ == ' ')
      } else {
        line prefixLength (_ == ' ')
      }
      @inline def prevLinePrefixAfterDelimiter(offsetInLine: Int): Int =
        if (prevLine.length > offsetInLine) prevLine substring offsetInLine prefixLength (c => c == ' ' || c == '\t') else 0

      val wasSingleLine = element.getText.indexOf("\n") == element.getText.lastIndexOf("\n")
      val lines = element.getText.split("\n")

      if (wasSingleLine || lines.length == 3 &&
      (lines(0).endsWith("(")&&lines(2).trim.startsWith(")") || lines(0).endsWith("{")&&lines(2).trim.startsWith("}"))) {
        val trimmedStartLine = getLineByNumber(document.getLineNumber(offset) - 1).trim()
        val needInsertNLBefore =
          (!trimmedStartLine.startsWith(multilineQuotes) || trimmedStartLine.contains("\"\"\" + \"\"\"")) &&
            scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE

        
        @inline def fullPrefixLength(line: String) = prefixLength(line) + multilineQuotesLength + (if (needInsertNLBefore) 1 else 0)
        @inline def getPrefix(line: String) = line.substring(0, prefixLength(line))
        @inline def insertStripMargin(extraSpace: Boolean) {
          if (MultilineStringEnterHandler.needAddStripMargin(element, marginChar)) {
            document.insertString(element.getTextRange.getEndOffset + (if (extraSpace) 1 else 0),
              if (marginChar == "|") ".stripMargin" else ".stripMargin(\'" + marginChar + "\')")
          }
        }

        if (!wasSingleLine) {
          caretShiftHor = scalaSettings.MULTILINE_STRING_SUPORT match {
            case ScalaCodeStyleSettings.MULTILINE_STRING_ALL =>
              insertStripMargin(false)
              val currentPrefix = fullPrefixLength(currentLine)
              currentPrefix + prevLinePrefixAfterDelimiter(currentPrefix)
            case ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT =>
              currentLine.length + (if (!needInsertNLBefore) 0 else 2)
          }

          val insertToNextLine = scalaSettings.MULTILINE_STRING_SUPORT match {
            case ScalaCodeStyleSettings.MULTILINE_STRING_ALL => marginChar
            case _ => ""
          }

          if (needInsertNLBefore && scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_ALL)
            caretShiftVert = -1

          document.insertString(document.getLineStartOffset(prevLineNumber + 2) + getLineByNumber(prevLineNumber + 2).prefixLength(_ == ' '),
            (if (scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT)
              getSmartSpaces(caretShiftHor - nextLine.prefixLength(_ == ' ') ) else getPrefix(currentLine)) + insertToNextLine)
        }

        if (needInsertNLBefore) insertNewLine(elementOffset)

        if (element.getText.substring(offset - elementOffset) == multilineQuotes) {
          caretShiftVert = -1
          selectBySettings {
            caretShiftHor = multilineQuotesLength + (if (needInsertNLBefore) 0 else 1) //e.i. "\"\"\"".length
          } {
            caretShiftHor = myIndentSize + 1//multilineQuotesLength + (if (useTabs) 0 else 1) // e.i. "\"\"\"".length + marginChar.length
          }

          if (scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_ALL) {
            insertStripMargin(needInsertNLBefore)

            if (!needInsertNLBefore) {
              caretShiftVert = 0
              caretShiftHor = 1
            }
            document.insertString(offset, marginChar + "\n")
          } else {
            insertNewLine(offset)
          }
        } else {
          val needInsertIndentInt =
            if (needInsertNLBefore)
              settings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).INDENT_SIZE
            else 0
          selectBySettings {
            document.insertString(offset, getSmartSpaces(multilineQuotesLength + needInsertIndentInt))
          } {
            document.insertString(offset, getSmartSpaces(needInsertIndentInt) + marginChar)
          }
        }
      } else {
        lazy val isCurrentLineEmpty = currentLine.trim.length == 0
        lazy val currentLineOffset = document.getLineStartOffset(prevLineNumber + 1)

        val isPrevLineFirst = prevLine startsWith multilineQuotes
        lazy val isPrevLineTrimmedFirst = prevLine.trim startsWith multilineQuotes
        /*lazy*/ val prevLineStartOffset = document getLineStartOffset prevLineNumber

        val wsPrefix = if (isPrevLineFirst) {
          prevLinePrefixAfterDelimiter(multilineQuotesLength) + multilineQuotesLength
        }
        else {
          prevLine.prefixLength(c => c == ' ' || c == '\t')
        }

        val prefixStriped = prevLine.substring(wsPrefix)
        
        if (scalaSettings.MULTILINE_STRING_SUPORT == ScalaCodeStyleSettings.MULTILINE_STRING_QUOTES_AND_INDENT ||
          !prefixStriped.startsWith(marginChar) && !prefixStriped.startsWith(multilineQuotes)) {
          if (prevLineStartOffset < elementOffset) {
            val elementStart = prevLine.indexOf(multilineQuotes) + multilineQuotesLength
            val prevLineWsPrefixAfterQuotes = prevLinePrefixAfterDelimiter(elementStart)

            if (isPrevLineTrimmedFirst) caretShiftHor = elementStart + prevLineWsPrefixAfterQuotes

            val spacesToInsert = if (isPrevLineTrimmedFirst) multilineQuotesLength + prevLineWsPrefixAfterQuotes else
              (if (isCurrentLineEmpty) elementStart else elementStart - wsPrefix) + prevLineWsPrefixAfterQuotes
            document.insertString(currentLineOffset, getSmartSpaces(spacesToInsert))
          } else if (isCurrentLineEmpty && prevLine.length > 0) {
            caretShiftHor = if (!useTabs) wsPrefix else prefixLength(prevLine)
          } else if (prevLine.trim.length == 0) {
            if (useTabs) {
//              document.insertString(currentLineOffset, spaces )
              caretShiftHor = caretModel.getVisualPosition.getColumn//spaces.length + currentLine.length
            } else {
              document.insertString(prevLineStartOffset, getSmartSpaces(caretModel.getVisualPosition.getColumn))
              caretShiftHor = currentLine.length
            }

            
          } else if (isPrevLineTrimmedFirst) {
            val wsAfterQuotes = prevLinePrefixAfterDelimiter(wsPrefix + multilineQuotesLength) + multilineQuotesLength
            document.insertString(offset, getSmartSpaces(wsAfterQuotes))
          }
        } else {
          val wsAfterMargin =
            if (isPrevLineFirst) multilineQuotesLength else prevLinePrefixAfterDelimiter(wsPrefix + 1)

          if (!currentLine.trim.startsWith(marginChar)) {
            if (prevLine.endsWith("{") && nextLine.trim.startsWith("}") || prevLine.endsWith("(") && nextLine.trim.startsWith(")")) {
              document.insertString(document.getLineStartOffset(prevLineNumber + 2) + 
                nextLine.prefixLength(c => c == ' ' || c == '\t'), marginChar + getSpaces(wsAfterMargin))
            }

            document.insertString(offset, (if (caretShiftHor != 1) marginChar else "") + getSpaces(wsAfterMargin))
            caretShiftHor = wsAfterMargin + 1 
          }
        }
      }

      PsiDocumentManager getInstance file.getProject commitDocument document
      val anotherElement = file.findElementAt(offset - 1)
      assert(anotherElement != null)

      def getReformatRange(initialElement: PsiElement): TextRange = {
        initialElement match {
          case l: ScLiteral => getReformatRange(l.getParent)
          case ref: ScReferenceExpression => getReformatRange(ref.getParent)
          case inf: ScInfixExpr => inf.getTextRange
          case _ => null
        }
      }

      CodeStyleManager.getInstance(file.getProject).adjustLineIndent(file,
        Option(getReformatRange(anotherElement.getParent)) getOrElse anotherElement.getTextRange)

      if (caretShiftHor != 0 || caretShiftVert != 0) {
        moveCaret(caretShiftVert, caretShiftHor)
//        caretModel.moveCaretRelatively(caretShiftHor, caretShiftVert, false, false, false)
      }

      val newLineNumber = document.getLineNumber(caretModel.getOffset)
      val newLine = getLineByNumber(newLineNumber)
      val newSpaces = caretModel.getVisualPosition.getColumn - newLine.length
      if (newSpaces > 0 && !useTabs) {
        document.insertString(document.getLineEndOffset(newLineNumber), getSmartSpaces(newSpaces))
      }
    }

    Result.Stop
  }
}

object MultilineStringEnterHandler {
  val multilineQuotes = "\"\"\""
  val multilineQuotesLength = multilineQuotes.length

  def needAddMethodCallToMLString(stringElement: PsiElement, methodName: String): Boolean = {
    var parent = stringElement.getParent

    do {
      parent match {
        case ref: ScReferenceElement => //if (ref.nameId.getText == methodName) return false
        case l: ScLiteral => if (!l.isMultiLineString) return false
        case i: ScInfixExpr => if (i.operation.getText == methodName) return false
        case call: ScMethodCall =>
          if (Option(call.getEffectiveInvokedExpr).forall {
            case expr: ScExpression => expr.getText endsWith "." + methodName
            case _ => false
          }) return false
        case _: ScParenthesisedExpr =>
        case _ => return true
      }

      parent = parent.getParent
    } while (parent != null)

    true
  }

  def needAddStripMargin(element: PsiElement, marginChar: String) = {
    findAllMethodCallsOnMLString(element, "stripMargin").isEmpty
  }

  def getMarginChar(element: PsiElement): String = {
    val calls = findAllMethodCallsOnMLString(element, "stripMargin")
    val defaultMargin = CodeStyleSettingsManager.getInstance(element.getProject).getCurrentSettings.
      getCustomSettings(classOf[ScalaCodeStyleSettings]).MARGIN_CHAR + ""


    if (calls.isEmpty) return defaultMargin

    calls.apply(0).headOption match {
      case Some(a: ScLiteral) => Option(a.getValue) map (_.toString) getOrElse defaultMargin
      case _ => defaultMargin
    }
  }

  def findAllMethodCallsOnMLString(stringElement: PsiElement, methodName: String): Array[Array[ScExpression]] = {
    val calls = new ArrayBuffer[Array[ScExpression]]()
    def callsArray = calls.toArray

    var parent = stringElement.getParent
    var prevParent = stringElement

    do {
      parent match {
        case lit: ScLiteral => if (!lit.isMultiLineString) return Array.empty
        case inf: ScInfixExpr =>
          if (inf.operation.getText == methodName){
            if (prevParent != parent.getFirstChild) return callsArray
            calls += Array(inf.rOp)
          }
        case call: ScMethodCall =>
          call.getEffectiveInvokedExpr match {
            case ref: ScReferenceExpression if ref.refName == methodName => calls += call.args.exprsArray
            case _ =>
          }
        case exp: ScReferenceExpression =>
          if (!exp.getParent.isInstanceOf[ScMethodCall]) {
            calls += Array[ScExpression]()
          }
        case _: ScParenthesisedExpr =>
        case _ => return callsArray
      }

      prevParent = parent
      parent = parent.getParent
    } while (parent != null)

    callsArray
  }

  def containsArgs(currentArgs: Array[Array[ScExpression]], argsToFind: String*): Boolean = {
    val myArgs = argsToFind.sorted

    for (arg <- currentArgs) {
      val argsString = arg.map(_.getText).sorted

      if (myArgs.sameElements(argsString) || myArgs.reverse.sameElements(argsString)) return true
    }

    false
  }

}
