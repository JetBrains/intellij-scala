package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.compiler.impl.CompilerErrorTreeView
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.{PsiErrorElement, PsiFile}
import com.intellij.ui.content.{ContentFactory, MessageView}
import com.intellij.util.ui.MessageCategory
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.server._
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

    val runType = getRunType(project)

    if (runType != NonServer)
      CompileServerLauncher.ensureServerRunning(project)

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

        val onError = (msg: String) => {
          NotificationUtil.builder(project, msg).setGroup(
            "Scala").setNotificationType(NotificationType.ERROR).setTitle(CONFIG_ERROR_HEADER).show()
        }

        val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, worksheetPrinter, None, auto)

        task.start(new Runnable {
          override def run() {
            //todo smth with exit code
            try {
              val module = RunWorksheetAction getModuleFor worksheetFile
              
              if (module == null) onError("Can't find Scala module to run") else new RemoteServerConnector(
                module, tempFile, outputDir, name
              ).compileAndRun(new Runnable {
                override def run() {
                  if (runType == OutOfProcessServer) callback(name, outputDir.getAbsolutePath)
                }
              }, worksheetVirtual, consumer)
            }
            catch {
              case ex: IllegalArgumentException => onError(ex.getMessage)
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

            MigrationApiImpl.openMessageView(project, errorContent, treeError)
            editor.getCaretModel moveToLogicalPosition pos
          }
        })

      case _ =>
    }
  }
}

object WorksheetCompiler extends WorksheetPerFileConfig {
  private val MAKE_BEFORE_RUN = new FileAttribute("ScalaWorksheetMakeBeforeRun", 1, true)
  private val CP_MODULE_NAME = new FileAttribute("ScalaWorksheetModuleForCp", 1, false)
  private val ERROR_CONTENT_NAME = "Worksheet errors"

  val CONFIG_ERROR_HEADER = "Worksheet configuration error:"

  def getCompileKey: Key[String] = Key.create[String]("scala.worksheet.compilation")
  def getOriginalFileKey: Key[String] = Key.create[String]("scala.worksheet.original.file")

  def isMakeBeforeRun(file: PsiFile): Boolean = isEnabled(file, MAKE_BEFORE_RUN)

  def setMakeBeforeRun(file: PsiFile, isMake: Boolean): Unit = {
    setEnabled(file, MAKE_BEFORE_RUN, isMake)
  }
  
  def getModuleForCpName(file: PsiFile): Option[String] = FileAttributeUtilCache.readAttribute(CP_MODULE_NAME, file)
  
  def setModuleForCpName(file: PsiFile, moduleName: String): Unit = FileAttributeUtilCache.writeAttribute(CP_MODULE_NAME, file, moduleName)

  def getRunType(project: Project): WorksheetMakeType = {
    if (ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED) {
      if (ScalaProjectSettings.getInstance(project).isInProcessMode)
        InProcessServer
      else OutOfProcessServer
    }
    else NonServer
  }
}
