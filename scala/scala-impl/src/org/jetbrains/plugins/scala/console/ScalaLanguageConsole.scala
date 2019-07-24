package org.jetbrains.plugins.scala.console

import java.awt.{Color, Font}

import com.intellij.execution.console.{ConsoleHistoryController, LanguageConsoleImpl}
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.SideBorder
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable

class ScalaLanguageConsole(module: Module)
  extends LanguageConsoleImpl(new ScalaLanguageConsole.Helper(
    module.getProject,
    ScalaLanguageConsoleView.ScalaConsole,
    ScalaLanguage.INSTANCE
  )) {

  private val textBuffer = new StringBuilder
  private var scalaFile  = createContextFile("")

  resetFileContext()

  def getHistory: String = textBuffer.toString()

  override def attachToProcess(processHandler: ProcessHandler): Unit = {
    super.attachToProcess(processHandler)
    val controller = new ConsoleHistoryController(ScalaLanguageConsoleView.ScalaConsoleRootType, null, this)
    controller.install()

    ScalaConsoleInfo.addConsole(this, controller, processHandler)
  }

  import ConsoleState.ConsoleState

  private var state: ConsoleState = ConsoleState.Init

  override def print(text: String, contentType: ConsoleViewContentType): Unit = {
    updateState(text, contentType)
    val contentTypeAdjusted = adjustContentType(state, contentType)
    super.print(text, contentTypeAdjusted)
    updatePrompt()
    updateTempResultValueDefinitions(text, contentType)
  }

  private def updateState(text: String, contentType: ConsoleViewContentType): Unit =
    state = stateFor(text, contentType)

  private def stateFor(text: String, contentType: ConsoleViewContentType): ConsoleState = {
    import ConsoleState._
    import ConsoleViewContentType._
    import ScalaLanguageConsole._

    // expecting only first process output line to be args
    // welcome text is considered to be everything between first line and first prompt occurrence
    def stateForNormalOutput: ConsoleState = text.trim match {
      case ScalaPromptIdleText                   => Ready
      case ScalaPromptInputInProgressTextTrimmed => InputIsInProgress
      case _                                     =>
        state match {
          case Init | PrintingSystemOutput => PrintingWelcomeMessage
          case state                       => state
        }
    }

    contentType match {
      case NORMAL_OUTPUT => stateForNormalOutput
      case SYSTEM_OUTPUT => PrintingSystemOutput
      case _             => state
    }
  }

  private def adjustContentType(state: ConsoleState, contentType: ConsoleViewContentType): ConsoleViewContentType =
    state match {
      case ConsoleState.PrintingWelcomeMessage => WelcomeTextContentType
      case _ => contentType
    }

  private def updatePrompt(): Unit = {
    val prompt = if (state == ConsoleState.InputIsInProgress) ScalaPromptInputInProgressText else ScalaPromptIdleText
    if (prompt != getPrompt) {
      super.setPrompt(prompt)
      updateUI() // note that some blinking can still take place when sending multiline content to the REPL process
    }
  }

  private def updateTempResultValueDefinitions(output: String, contentType: ConsoleViewContentType): Unit =
    if (contentType == ConsoleViewContentType.NORMAL_OUTPUT) {
      output match {
        case tempValRegex(resWithType) =>
          //adding dummy declaration just to make res values visible in completion list, real value is known by underlying REPL
          ApplicationManager.getApplication.invokeLater { () =>
            processDeclarations(s"""val $resWithType = null""")
          }
        case _ =>
      }
    }

  /** HACK: We do not want console editor prompt to be added to the view editor not to duplicate the real one.
   * Real prompt from process output is added to console view editor.
   * It is hidden when it is in the last line, but it is shown right after Enter press.
   * The edge case is when console content is cleaned, due to real prompt is cleaned as well.
   *
   * @see [[ScalaLanguageConsole.Helper.setupEditor]]
   */
  override def doAddPromptToHistory(): Unit = {
    val afterConsoleCleanAction = getEditor.getDocument.getTextLength == 0
    if (afterConsoleCleanAction) {
      getPrompt match {
        case null =>
        case prompt =>
          print(prompt, ConsoleViewContentType.NORMAL_OUTPUT)
      }
    }
  }

  private[console] def textSent(text: String): Unit = processDeclarations(text)

  private[console] def processDeclarations(text: String): Unit = {
    textBuffer.append(text)
    scalaFile = createContextFile(textBuffer.toString())

    val types  = new mutable.HashMap[String, TextRange]
    val values = new mutable.HashMap[String, (TextRange, Boolean)]

    def addValue(name: String, range: TextRange, replaceWithPlaceholder: Boolean): Unit = {
      values.get(name) match {
        case Some((oldRange, r)) =>
          val newText =
            if (r) "_" + StringUtil.repeatSymbol(' ', oldRange.getLength - 1)
            else StringUtil.repeatSymbol(' ', oldRange.getLength)
          textBuffer.replace(oldRange.getStartOffset, oldRange.getEndOffset, newText)
        case None =>
      }

      values.put(name, (range, replaceWithPlaceholder))
    }

    def addType(name: String, range: TextRange): Unit = {
      types.get(name) match {
        case Some(oldRange) =>
          val newText = StringUtil.repeatSymbol(' ', oldRange.getLength)
          textBuffer.replace(oldRange.getStartOffset, oldRange.getEndOffset, newText)
        case None =>
      }

      types.put(name, range)
    }

    scalaFile.getChildren.foreach {
      case v: ScValue     => v.declaredElements.foreach(td => addValue(td.name, td.nameId.getTextRange, replaceWithPlaceholder = true))
      case v: ScVariable  => v.declaredElements.foreach(td => addValue(td.name, td.nameId.getTextRange, replaceWithPlaceholder = true))
      case f: ScFunction  => addValue(f.name, f.getTextRange, replaceWithPlaceholder = false)
      case o: ScObject    => addValue(o.name, o.getTextRange, replaceWithPlaceholder = false)
      case c: ScClass     => addType(c.name, c.nameId.getTextRange)
      case c: ScTrait     => addType(c.name, c.nameId.getTextRange)
      case t: ScTypeAlias => addType(t.name, t.nameId.getTextRange)
      case _              => //do nothing
    }

    scalaFile = createContextFile(textBuffer.toString())
    resetFileContext()
  }

  private def createContextFile(text: String): ScalaFile = {
    val textFinal = {
      val content = if (text.nonEmpty) s"$text;\n" else ""
      val dummyContent = "1"
      content + dummyContent
    }

    val file = ScalaPsiElementFactory.createScalaFileFromText(textFinal)(getProject)
    file.putUserData(ModuleUtilCore.KEY_MODULE, module)
    file
  }

  private def resetFileContext(): Unit = getFile match {
    case scala: ScalaFile =>
      scala.context = scalaFile
      scala.child = scalaFile.getLastChild
    case _ =>
  }
}

private object ScalaLanguageConsole {
  private val ScalaPromptIdleText                   = "scala>"
  private val ScalaPromptInputInProgressText        = "     |"
  private val ScalaPromptInputInProgressTextTrimmed = ScalaPromptInputInProgressText.trim

  //example: res5: Long = 42
  private val tempValRegex = """^(res\d+: .*?)=.*\n?""".r

  private val WelcomeTextContentType: ConsoleViewContentType = {
    val attributes = new TextAttributes()
    attributes.setFontType(Font.BOLD)
    new ConsoleViewContentType("SCALA_CONSOLE_WELCOME_TEXT", attributes)
  }

  private object ConsoleState extends Enumeration {
    type ConsoleState = Value
    val Init, PrintingSystemOutput, PrintingWelcomeMessage, Ready, InputIsInProgress = Value
  }

  private class Helper(project: Project, title: String, language: Language)
    extends LanguageConsoleImpl.Helper(project, new LightVirtualFile(title, language, "")) {

    /** HACK: we want the caret to be right after `scala>` prompt, but we actually have two separate editors:<br>
     * 1) view editor which we can't edit, it is in view-mode (this.getEditor)<br>
     * 2) console editor in which actually write code (this.getConsoleEditor)<br>
     * So we hide the real prompt from Scala REPL process from view editor (hide last line) and pretend
     * that console editor prompt is the real one.
     */
    override def setupEditor(editor: EditorEx): Unit = {
      super.setupEditor(editor)
      ScalaConsoleInfo.setIsConsole(editor, flag = true)
      editor.getSettings.setAdditionalLinesCount(-1)
    }
  }

  def builderFor(module: Module): TextConsoleBuilderImpl = new Builder(module)

  private class Builder(module: Module) extends TextConsoleBuilderImpl(module.getProject) {

    override def createConsole: LanguageConsoleImpl = {
      val consoleView = new ScalaLanguageConsole(module)
      ScalaConsoleInfo.setIsConsole(consoleView.getFile, flag = true)

      //pretend that we are a prompt from Scala REPL process
      consoleView.setPrompt(ScalaPromptIdleText)
      consoleView.setPromptAttributes(ConsoleViewContentType.NORMAL_OUTPUT)

      //drawDebugBorders(consoleView)
      consoleView
    }

    //noinspection ScalaUnusedSymbol
    private def drawDebugBorders(consoleView: ScalaLanguageConsole): Unit = {
      val mask      = SideBorder.ALL
      val thickness = 2

      consoleView.getComponent.setBorder(new SideBorder(Color.RED, mask, thickness))

      val viewEditor = consoleView.getEditor
      viewEditor.getComponent.setBorder(new SideBorder(Color.GREEN, mask, thickness))
      viewEditor.getContentComponent.setBorder(new SideBorder(Color.ORANGE, mask, thickness))

      val consoleEditor = consoleView.getConsoleEditor
      consoleEditor.getComponent.setBorder(new SideBorder(Color.BLUE, mask, 2 * thickness))
    }
  }
}