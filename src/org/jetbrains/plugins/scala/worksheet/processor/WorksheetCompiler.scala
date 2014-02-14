package org.jetbrains.plugins.scala
package worksheet.processor

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.worksheet.server.{NonServer, OutOfProcessServer, InProcessServer, RemoteServerConnector}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.compiler.progress.CompilerTask
import org.jetbrains.plugins.scala.compiler.{ScalaApplicationSettings, ScalacSettings, CompileServerLauncher}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala
import org.jetbrains.plugins.scala.worksheet.actions.CleanWorksheetAction

/**
 * User: Dmitry Naydanov
 * Date: 1/15/14
 */
class WorksheetCompiler {
  /**
   * @param callback (Name, AddToClasspath)
   */
  def compileAndRun(editor: Editor, worksheetFile: ScalaFile, callback: (String, String) => Unit, ifEditor: Option[Editor]) {
    val (iteration, tempFile, outputDir) = WorksheetBoundCompilationInfo.updateOrCreate(worksheetFile.getVirtualFile.getCanonicalPath, worksheetFile.getName)
    
    WorksheetSourceProcessor.process(worksheetFile, ifEditor, iteration) map {
      case (code, name) =>
        val project = worksheetFile.getProject
        FileUtil.writeToFile(tempFile, code)

        val task = new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

        val worksheetPrinter =
          WorksheetEditorPrinter.newWorksheetUiFor(editor, worksheetFile.getVirtualFile)
        worksheetPrinter.scheduleWorksheetUpdate()
        val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, worksheetPrinter, None)
        
        val runType = (ScalaApplicationSettings.getInstance().COMPILE_SERVER_ENABLED, 
          ScalaProjectSettings.getInstance(project).isInProcessMode) match {
          case (true, true) => InProcessServer
          case (true, false) => OutOfProcessServer
          case (false, _) => NonServer
        }
        
        task.start(new Runnable {
          override def run() {
            if (runType != NonServer) ensureServerRunning(project) else ensureNotRunning(project)
            //todo smth with exit code
            new RemoteServerConnector(ModuleUtilCore.findModuleForFile(worksheetFile.getVirtualFile, project), tempFile, outputDir).compileAndRun(new Runnable {
              override def run() {
                if (runType == OutOfProcessServer) callback(name, outputDir.getAbsolutePath)
              }
            }, worksheetFile.getVirtualFile, consumer, name, runType)
          }
        }, new Runnable {override def run() {}})
    }
  }
  
  private def ensureServerRunning(project: Project) {
    val launcher = CompileServerLauncher.instance
    if (!launcher.running) CompileServerLauncher.instance tryToStart project
  }
  
  private def ensureNotRunning(project: Project) {
    val launcher = CompileServerLauncher.instance
    if (launcher.running) launcher.stop(project)
  }
}

object WorksheetCompiler {
  def getCompileKey = Key.create[String]("scala.worksheet.compilation")
  def getOriginalFileKey = Key.create[String]("scala.worksheet.original.file")
}
