package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.compiler.impl.CompilerErrorTreeView
import com.intellij.compiler.progress.CompilerTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Disposer, Key}
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.wm.{ToolWindowId, ToolWindowManager}
import com.intellij.psi.{PsiErrorElement, PsiFile}
import com.intellij.ui.content.{Content, ContentFactory, MessageView}
import com.intellij.util.ui.MessageCategory
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaApplicationSettings}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.server.{InProcessServer, NonServer, OutOfProcessServer, RemoteServerConnector}
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter

/**
 * User: Dmitry Naydanov
 * Date: 1/15/14
 */
class WorksheetCompiler {
  /**
   * @param callback (Name, AddToClasspath)
   */
  def compileAndRun(editor: Editor, worksheetFile: ScalaFile, callback: (String, String) => Unit,
                    ifEditor: Option[Editor], auto: Boolean) {
    import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler._
    
    val worksheetVirtual = worksheetFile.getVirtualFile
    val (iteration, tempFile, outputDir) = WorksheetBoundCompilationInfo.updateOrCreate(worksheetVirtual.getCanonicalPath, worksheetFile.getName)

    val project = worksheetFile.getProject
    val runType = (ScalaApplicationSettings.getInstance().COMPILE_SERVER_ENABLED,
      ScalaProjectSettings.getInstance(project).isInProcessMode) match {
      case (true, true) => InProcessServer
      case (true, false) => OutOfProcessServer
      case (false, _) => NonServer
    }

    if (runType != NonServer) ensureServerRunning(project) else ensureNotRunning(project)


    val contentManager = MessageView.SERVICE.getInstance(project).getContentManager
    val oldContent = contentManager findContent ERROR_CONTENT_NAME
    if (oldContent != null) contentManager.removeContent(oldContent, true)

    WorksheetSourceProcessor.process(worksheetFile, ifEditor, iteration) match {
      case Left((code, name)) =>
        FileUtil.writeToFile(tempFile, code)

        val task = new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

        val worksheetPrinter =
          WorksheetEditorPrinter.newWorksheetUiFor(editor, worksheetVirtual)
        worksheetPrinter.scheduleWorksheetUpdate()

        val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, worksheetPrinter, None, auto)

        task.start(new Runnable {
          override def run() {
            try {
              //todo smth with exit code
              new RemoteServerConnector(
                RunWorksheetAction getModuleFor worksheetFile, tempFile, outputDir
              ).compileAndRun(new Runnable {
                override def run() {
                  if (runType == OutOfProcessServer) callback(name, outputDir.getAbsolutePath)
                }
              }, worksheetVirtual, consumer, name, runType)
            }
          }
        }, new Runnable {override def run() {}})
      case Right(errorMessage: PsiErrorElement) =>
        if (auto) return
        val pos = editor.offsetToLogicalPosition(errorMessage.getTextOffset)

        val treeError = new CompilerErrorTreeView(project, null)

        ApplicationManager.getApplication.invokeLater(new Runnable {
          override def run() {
            val file = errorMessage.getContainingFile.getVirtualFile
            if (file == null || !file.isValid) return

            treeError.addMessage(MessageCategory.ERROR, Array(errorMessage.getErrorDescription),
              file, pos.line, pos.column, null)

            val errorContent = ContentFactory.SERVICE.getInstance.createContent(treeError.getComponent, ERROR_CONTENT_NAME, true)
            contentManager addContent errorContent
            contentManager setSelectedContent errorContent

            openMessageView(project, errorContent, treeError)
            editor.getCaretModel moveToLogicalPosition pos
          }
        })

      case _ =>
    }
  }

  private def openMessageView(project: Project, content: Content, treeView: CompilerErrorTreeView) {
    val commandProcessor = CommandProcessor.getInstance()
    commandProcessor.executeCommand(project, new Runnable {
      override def run() {
        Disposer.register(content, treeView, null)
        val messageView = ServiceManager.getService(project, classOf[MessageView])
        messageView.getContentManager setSelectedContent content

        val toolWindow = ToolWindowManager getInstance project getToolWindow ToolWindowId.MESSAGES_WINDOW
        if (toolWindow != null) toolWindow.show(null)
      }
    }, null, null)
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {
  private val MAKE_BEFORE_RUN = new FileAttribute("ScalaWorksheetMakeBeforeRun", 1, true)
  private val ERROR_CONTENT_NAME = "Worksheet errors"

  def getCompileKey = Key.create[String]("scala.worksheet.compilation")
  def getOriginalFileKey = Key.create[String]("scala.worksheet.original.file")


  def ensureServerRunning(project: Project) {
    val launcher = CompileServerLauncher.instance
    if (!launcher.running) CompileServerLauncher.instance tryToStart project
  }

  def ensureNotRunning(project: Project) {
    val launcher = CompileServerLauncher.instance
    if (launcher.running) launcher.stop(project)
  }

  def isMakeBeforeRun(file: PsiFile) = isEnabled(file, MAKE_BEFORE_RUN)

  def setMakeBeforeRun(file: PsiFile, isMake: Boolean) = {
    setEnabled(file, MAKE_BEFORE_RUN, isMake)
  }
}
