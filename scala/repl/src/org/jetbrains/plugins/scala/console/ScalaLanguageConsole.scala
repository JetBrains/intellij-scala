package org.jetbrains.plugins.scala.console

import com.intellij.execution.console.{ConsoleHistoryController, ConsoleRootType, LanguageConsoleImpl}
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.{Gray, JBColor, SideBorder}
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole.*
import org.jetbrains.plugins.scala.console.actions.ScalaConsoleExecuteAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

import java.awt.Font
import scala.collection.mutable

class ScalaLanguageConsole(module: Module, language: Language)
  extends LanguageConsoleImpl(new ScalaLanguageConsole.Helper(
    module.getProject,
    ScalaConsoleTitle,
    language
  )) {

  def this(module: Module) = {
    this(module, if (module.hasScala3) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE)
  }

  private val textBuffer = new StringBuilder
  private var scalaFile  = createContextFile("")

  resetFileContext()

  def getHistory: String = textBuffer.toString()

  override def attachToProcess(processHandler: ProcessHandler): Unit = {
    super.attachToProcess(processHandler)
    val controller = new ConsoleHistoryController(ScalaConsoleRootType, null, this)
    controller.install()

    ScalaConsoleInfo.addConsole(this, controller, processHandler)
  }

  private var state: ConsoleState = ConsoleState.Init

  override def print(text: String, contentType: ConsoleViewContentType): Unit = {
    updateState(text, contentType)
    val contentTypeAdjusted = adjustContentType(state, contentType)
    super.print(text, contentTypeAdjusted)
    updatePrompt()
    updateConsoleEditorPlaceholder()
    updateTempResultValueDefinitions(text, contentType)
  }

  private def updateState(text: String, contentType: ConsoleViewContentType): Unit = {
    val newState = stateFor(text, contentType)

    if (Log.isDebugEnabled) {
      val tid = Thread.currentThread().getId
      val stateTransferMessage = f"${state.getClass.getSimpleName}%23s -> ${newState.getClass.getSimpleName}%-23s"
      Log.debug(f"# $tid%5s $stateTransferMessage content type: $contentType%-25s text: $text".stripTrailing())
    }

    // TODO: override `LanguageConsoleImpl.getMinHistoryLineCount` and return `1` when it's made `protected
    //  Details: Scala 3 doesn't print any welcome message and prints prompt right after the system output.
    //  Thus we have only 2 lines in the history viewer when the prompt is shown.
    //  Our hack in `ScalaLanguageConsole.Helper.setupEditor` doesn't work: we can't hide first `scala>` prompt because
    //  minHistoryLineCount=2
    state -> newState match {
      case ConsoleState.PrintingSystemOutput -> ConsoleState.Ready =>
        super.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
      case _ =>
    }

    state = newState
  }

  private def stateFor(text: String, contentType: ConsoleViewContentType): ConsoleState = {
    import ConsoleState.*
    import ConsoleViewContentType.*
    import ScalaLanguageConsole.*
    if (state == Terminated)
      return state

    contentType match {
      case SYSTEM_OUTPUT =>
        state match {
          //system output can be printed multiple times in the very beginning:
          //line 1 contains command line:
          //"java.exe ... dotty.tools.repl.Main -usejavacp"
          //line 2 can contain debug information if someone starts Scala REPL in debug mode:
          //"Connected to the target VM, address: '127.0.0.1:62877', transport: 'socket'"
          case Init | PrintingSystemOutput => PrintingSystemOutput
          case _ => Terminated
        }
      case _ =>
        // expecting only first process output line to be args
        // welcome text is considered to be everything between first line and first prompt occurrence
        val textTrimmed = text.trim
        textTrimmed match {
          case ScalaPromptIdleText  =>
            //In Scala 3 colors are not disabled even in JLine dumb mode, so we update prompt color to be consistent with REPL output
            //see https://youtrack.jetbrains.com/issue/SCL-20177
            //see https://github.com/jline/jline3/issues/814
            this.setPromptAttributes(contentType)
            Ready
          case ScalaPromptInputInProgressTextTrimmed =>
            InputIsInProgress
          case _ =>
            state match {
              case Init | PrintingSystemOutput => PrintingWelcomeMessage
              case Ready                       => Evaluating // consider REPL to be in Evaluating state until `scala>` prompt is printed
              case InputIsInProgress           => Ready
              case state                       => state
            }
        }
    }
  }

  private def adjustContentType(state: ConsoleState, contentType: ConsoleViewContentType): ConsoleViewContentType =
    state match {
      case ConsoleState.PrintingWelcomeMessage => WelcomeTextContentType
      case _ => contentType
    }

  private def updatePrompt(): Unit = {
    import ConsoleState.*
    val prompt = state match {
      case Ready             => ScalaPromptIdleText
      case InputIsInProgress => ScalaPromptInputInProgressText
      case Init |
           PrintingSystemOutput |
           PrintingWelcomeMessage |
           Terminated |
           Evaluating        => ScalaPromptEmpty

    }
    if (prompt != getPrompt) {
      super.setPrompt(prompt)
      updateUI() // note that some blinking can still take place when sending multiline content to the REPL process
    }
  }

  private val EditorPlaceholderAttributes: TextAttributes ={
    val attrs = new TextAttributes
    attrs.setForegroundColor(JBColor.LIGHT_GRAY)
    attrs.setFontType(Font.ITALIC)
    attrs
  }

  private val TransparentAttributes: TextAttributes = {
    val attrs = new TextAttributes()
    attrs.setForegroundColor(Gray.TRANSPARENT)
    attrs
  }

  private def updateConsoleEditorPlaceholder(): Unit = {
    val placeholderAttributes = if (state == ConsoleState.Ready) EditorPlaceholderAttributes else TransparentAttributes
    getConsoleEditor.setPlaceholderAttributes(placeholderAttributes)
  }

  private def updateTempResultValueDefinitions(output: String, contentType: ConsoleViewContentType): Unit =
    if (contentType == ConsoleViewContentType.NORMAL_OUTPUT) {
      output match {
        case tempValRegex(_, resWithType) =>
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

    val file = ScalaPsiElementFactory.createScalaFileFromText(textFinal, module.features)(getProject)
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

object ScalaLanguageConsole {
  private val Log = Logger.getInstance(classOf[ScalaLanguageConsole])

  private[console] val ScalaConsoleTitle: String = ScalaReplBundle.message("scala.console.config.display.name")

  private object ScalaConsoleRootType extends ConsoleRootType("scala", ScalaConsoleTitle)

  private val ScalaPromptIdleText            = "scala>"
  private val ScalaPromptInputInProgressText = "     |"
  private val ScalaPromptEmpty               = ""

  private val ScalaPromptInputInProgressTextTrimmed = ScalaPromptInputInProgressText.trim

  //example 1: res5: Long = 42
  //example 2: val res5: Long = 42
  private val tempValRegex = """(val )?(res\d+: .*?)=.*\n?""".r

  private val WelcomeTextContentType: ConsoleViewContentType = {
    val attributes = new TextAttributes()
    attributes.setFontType(Font.BOLD)
    new ConsoleViewContentType("SCALA_CONSOLE_WELCOME_TEXT", attributes)
  }

  //noinspection TypeAnnotation
  private sealed trait ConsoleState
  private object ConsoleState {
    object Init extends ConsoleState
    /**
     * Process command line printed as the first (folded) line in viewer editor, something like: {{{
     *   .../java.exe -Djline.terminal=NONE ... scala.tools.nsc.MainGenericRunner ...
     * }}}
     */
    object PrintingSystemOutput extends ConsoleState
    /**
     * in Scala2 REPL prints some system output info before showing the prompt: {{{
     *   Welcome to Scala 2.13.2 (OpenJDK 64-Bit Server VM, Java 11.0.9).
     *   Type in expressions for objectuation. Or try :help.
     * }}}
     */
    object PrintingWelcomeMessage extends ConsoleState
    object Ready extends ConsoleState
    object InputIsInProgress extends ConsoleState
    object Evaluating extends ConsoleState
    /** indicates that console is stopped and "Process finished with exit code N" is printed to the output  */
    object Terminated extends ConsoleState
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

      // setupEditor is called for both viewer & input editors
      if (editor.isViewer) {
        val hideLinesCount = 1
        editor.getSettings.setAdditionalLinesCount(-hideLinesCount)
      }
      else {
        setupEditorPlaceholderText(editor)
      }
    }

    private def setupEditorPlaceholderText(editor: EditorEx): Unit = {
      val executeCommandAction = ActionManager.getInstance.getAction(ScalaConsoleExecuteAction.ActionId)
      val executeCommandActionShortcutText = KeymapUtil.getFirstKeyboardShortcutText(executeCommandAction)

      editor.setPlaceholder(ScalaReplBundle.message("scala.language.console.placeholder.command.to.execute", executeCommandActionShortcutText))
      editor.setShowPlaceholderWhenFocused(true)
    }
  }

  private[console] def builderFor(module: Module): TextConsoleBuilderImpl = new Builder(module)

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
      val mask = SideBorder.ALL
      val thickness = 2

      consoleView.getComponent.setBorder(new SideBorder(JBColor.RED, mask, thickness))

      val viewEditor = consoleView.getEditor
      viewEditor.getComponent.setBorder(new SideBorder(JBColor.GREEN, mask, thickness))
      //viewEditor.getContentComponent.setBorder(new SideBorder(Color.ORANGE, mask, thickness))

      val consoleEditor = consoleView.getConsoleEditor
      consoleEditor.getComponent.setBorder(new SideBorder(JBColor.BLUE, mask, 2 * thickness))
    }
  }
}