package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.configurations._
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.Project
import java.lang.String
import org.jdom.Element
import com.intellij.openapi.options.SettingsEditor
import java.io._
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.openapi.util._
import com.intellij.execution._
import com.intellij.execution.process.{ProcessHandler, ProcessEvent, ProcessAdapter}
import ui.ConsoleViewContentType
import com.intellij.ide.util.EditorHelper
import com.intellij.psi._
import extensions._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.execution.impl.ConsoleViewImpl
import java.awt.event._
import org.jetbrains.plugins.scala.runner.BaseRunConfiguration
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.worksheet.MacroPrinter
import com.intellij.openapi.command.CommandProcessor

/**
 * @author Ksenia.Sautina
 * @author Dmutry Naydanov 
 * @since 10/15/12
 */
class WorksheetRunConfiguration(project: Project, configurationFactory: ConfigurationFactory, name: String)
  extends BaseRunConfiguration(project, configurationFactory, name) {

  val mainClass = "org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner"
  
  var worksheetField = ""
  var classToRunField = ""
  var addCpField = "" 

  private def worksheetError(msg: String) = throw new RuntimeConfigurationException(msg)
  
  def apply(params: WorksheetRunConfigurationForm) {
    javaOptions = params.getJavaOptions
    consoleArgs = params.getWorksheetOptions
    workingDirectory = params.getWorkingDirectory
    worksheetField = params.getWorksheetField
    setModule(params.getModule)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = createParams
        
        params.getClassPath.add(PathUtil.getJarPathForClass(classOf[MacroPrinter]))
        params.getClassPath.add(addCpField)
        params.getProgramParametersList addParametersString worksheetField
        params.getProgramParametersList addParametersString consoleArgs
        params.getProgramParametersList prepend classToRunField //IMPORTANT! this must be first program argument
        
        params
      }

      override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
        val file = new File(worksheetField)
        if (!file.exists()) worksheetError("Worksheet is not specified: file doesn't exist.")

        val virtualFile = LocalFileSystem.getInstance refreshAndFindFileByIoFile file
        if (virtualFile == null) worksheetError("Worksheet is not specified. File is not found.")

        val psiFile = PsiManager getInstance project findFile virtualFile
        if (psiFile == null) worksheetError("Worksheet is not specified: there is no cached file.")

        val processHandler = startProcess
        val runnerSettings = getRunnerSettings
        JavaRunConfigurationExtensionManager.getInstance.attachExtensionsToProcess(WorksheetRunConfiguration.this, processHandler, runnerSettings)

        val editor = EditorHelper openInEditor psiFile

        val worksheetPrinter = WorksheetEditorPrinter.newWorksheetUiFor(editor, virtualFile)
        val worksheetConsoleView = new ConsoleViewImpl(project, false)

        val myProcessListener: ProcessAdapter = new ProcessAdapter {
          @inline private[this] def closeHandler() {
            endProcess(processHandler)
            processHandler.removeProcessListener(this)
          }

          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (ConsoleViewContentType.NORMAL_OUTPUT == ConsoleViewContentType.getConsoleViewType(outputType)) {
              worksheetPrinter processLine text
            }
          }

          override def processTerminated(event: ProcessEvent): Unit = {
            worksheetPrinter.flushBuffer()
          }
        }

        worksheetPrinter.scheduleWorksheetUpdate()
        processHandler.addProcessListener(myProcessListener)

        editor.getContentComponent.addKeyListener(new KeyListener() {
          override def keyReleased(e: KeyEvent) {
            if (e.getKeyCode == KeyEvent.VK_ENTER) {
              invokeLater {
                inWriteAction {
                  CommandProcessor.getInstance() runUndoTransparentAction new Runnable {
                    override def run() {
                      val worksheetViewerDocument = WorksheetViewerInfo.getViewer(editor).getDocument
                      worksheetViewerDocument.insertString(worksheetViewerDocument.getTextLength, "\n")
                      PsiDocumentManager.getInstance(project).commitDocument(worksheetViewerDocument)
                    }
                  }
                }
              }
            }
          }

          override def keyTyped(e: KeyEvent) {
          }

          override def keyPressed(e: KeyEvent) { //wtf???
//            endProcess(processHandler)
//            processHandler.removeProcessListener(myProcessListener)
          }
        })

        val res = new DefaultExecutionResult(worksheetConsoleView, processHandler,
          createActions(worksheetConsoleView, processHandler, executor): _*)
        res
      }
    }

    state
  }

  def endProcess(processHandler: ProcessHandler) {
    try {
      processHandler.destroyProcess()
    } catch {
      case _: IOException => //ignore
    }
  }

  override def checkConfiguration() {
    super.checkConfiguration()

    val file = new File(worksheetField)
    if (!file.exists()) worksheetError("Worksheet is not specified: file does not exist.")

    val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)
    if (virtualFile == null) worksheetError("Worksheet is not specified: file is not found.")

    val psiFile: PsiFile = PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].getFileManager.getCachedPsiFile(virtualFile)
    if (psiFile == null) worksheetError("Worksheet is not specified: there is no cached file.")

    if (getModule == null) worksheetError("Module is not specified")

    JavaRunConfigurationExtensionManager checkConfigurationIsValid this
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new WorksheetRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    
    JDOMExternalizer.write(element, "worksheetOptions", consoleArgs)
    JDOMExternalizer.write(element, "worksheetField", worksheetField)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    
    consoleArgs = JDOMExternalizer.readString(element, "worksheetOptions")
    val ws = JDOMExternalizer.readString(element, "worksheetField")
    
    if (ws != null)worksheetField = ws
  }

}

object WorksheetRunConfiguration {
  val ContinueString = "     | "
  val PromptString   = "scala> "
}
