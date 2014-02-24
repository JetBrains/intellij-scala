package org.jetbrains.plugins.scala
package worksheet.processor

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.worksheet.server.{NonServer, OutOfProcessServer, InProcessServer, RemoteServerConnector}
import com.intellij.openapi.module.{ModuleManager, ModuleUtilCore}
import com.intellij.compiler.progress.CompilerTask
import org.jetbrains.plugins.scala.compiler.{ScalaApplicationSettings, CompileServerLauncher}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.actions.{CleanWorksheetAction, WorksheetFileHook}
import org.jetbrains.plugins.scala.config.ScalaFacet

/**
 * User: Dmitry Naydanov
 * Date: 1/15/14
 */
class WorksheetCompiler {
  /**
   * @param callback (Name, AddToClasspath)
   */
  def compileAndRun(editor: Editor, worksheetFile: ScalaFile, callback: (String, String) => Unit, ifEditor: Option[Editor]) {
    import WorksheetCompiler._
    
    val worksheetVirtual = worksheetFile.getVirtualFile
    val (iteration, tempFile, outputDir) = WorksheetBoundCompilationInfo.updateOrCreate(worksheetVirtual.getCanonicalPath, worksheetFile.getName)
    
    WorksheetSourceProcessor.process(worksheetFile, ifEditor, iteration) map {
      case (code, name) =>
        val project = worksheetFile.getProject
        FileUtil.writeToFile(tempFile, code)

        val task = new CompilerTask(project, s"Worksheet ${worksheetFile.getName} compilation", false, false, false, false)

        val worksheetPrinter =
          WorksheetEditorPrinter.newWorksheetUiFor(editor, worksheetVirtual)
        worksheetPrinter.scheduleWorksheetUpdate()
        val consumer = new RemoteServerConnector.CompilerInterfaceImpl(task, worksheetPrinter, None)
        
        val runType = (ScalaApplicationSettings.getInstance().COMPILE_SERVER_ENABLED, 
          ScalaProjectSettings.getInstance(project).isInProcessMode) match {
          case (true, true) => InProcessServer
          case (true, false) => OutOfProcessServer
          case (false, _) => NonServer
        }

        if (runType != NonServer) ensureServerRunning(project) else ensureNotRunning(project)
        
        task.start(new Runnable {
          override def run() {
            try {
              //todo smth with exit code
              new RemoteServerConnector(ModuleUtilCore.findModuleForFile(worksheetVirtual, project), tempFile, outputDir).compileAndRun(new Runnable {
                override def run() {
                  if (runType == OutOfProcessServer) callback(name, outputDir.getAbsolutePath)
                }
              }, worksheetVirtual, consumer, name, runType)
            }
          }
        }, new Runnable {override def run() {}})
    }
  }
}

object WorksheetCompiler {
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
}
