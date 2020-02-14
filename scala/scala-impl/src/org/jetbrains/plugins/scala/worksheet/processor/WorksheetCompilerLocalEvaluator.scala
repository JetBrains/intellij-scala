package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent}
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.{EvaluationCallback, WorksheetCompilerResult}
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinter

import scala.util.control.NonFatal

private object WorksheetCompilerLocalEvaluator {

  private val RunnerClassName = "org.jetbrains.plugins.scala.worksheet.PlainWorksheetRunner"

  // this method is only used when run type is [[org.jetbrains.plugins.scala.worksheet.server.OutOfProcessServer]]
   def executeWorksheet(scalaFile: ScalaFile, mainClassName: String, addToCp: String)
                       (callback: EvaluationCallback, worksheetPrinter: WorksheetEditorPrinter)
                       (implicit module: Module): Unit =
    invokeLater {
      try {
        val params: JavaParameters = createDefaultParameters(scalaFile, mainClassName, addToCp)
        val processHandler = params.createOSProcessHandler()

        WorksheetFileHook.updateStoppableProcess(scalaFile.getVirtualFile, Some(() => processHandler.destroyProcess()))
        processHandler.addProcessListener(new ProcessAdapter {
          override def processTerminated(event: ProcessEvent): Unit =
            WorksheetFileHook.updateStoppableProcess(scalaFile.getVirtualFile, None)
        })

        processHandler.addProcessListener(processListener(callback, worksheetPrinter))

        processHandler.startNotify()
      } catch {
        case NonFatal(ex: Throwable) =>
          callback(WorksheetCompilerResult.UnknownError(ex))
          throw ex
      }
    }

  private def createDefaultParameters(file: PsiFile, mainClassName: String, addToCp: String)
                                     (implicit module: Module): JavaParameters =
    createParameters(
      module = module,
      mainClassName = mainClassName,
      workingDirectory = Option(module.getProject.baseDir).fold("")(_.getPath),
      additionalCp = addToCp,
      worksheetField = file.getVirtualFile.getCanonicalPath
    )

  private def createParameters(module: Module,
                               mainClassName: String,
                               workingDirectory: String,
                               additionalCp: String,
                               worksheetField: String): JavaParameters = {
    val project = module.getProject

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()

    params.getClassPath.addScalaClassPath(module)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(project))
    params.getClassPath.addRunners()
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(RunnerClassName)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

    params.getClassPath.addRunners()
    params.getClassPath.add(additionalCp)
    params.getProgramParametersList.addParametersString(worksheetField)
    params.getProgramParametersList.prepend(mainClassName) //IMPORTANT! this must be first program argument

    params
  }

  private def processListener(callback: EvaluationCallback, worksheetPrinter: WorksheetEditorPrinter): ProcessAdapter =
    new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
        val isStdOutput = ConsoleViewContentType.getConsoleViewType(outputType) == ConsoleViewContentType.NORMAL_OUTPUT
        if (isStdOutput) {
          val text = event.getText
          worksheetPrinter.processLine(text)
        }
      }

      override def processTerminated(event: ProcessEvent): Unit = {
        worksheetPrinter.flushBuffer()
        val result = event.getExitCode match {
          case 0  => WorksheetCompilerResult.CompiledAndEvaluated
          case rc => WorksheetCompilerResult.ProcessTerminatedError(rc, event.getText)
        }
        callback.apply(result)
      }
    }
}
