package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent, ProcessTerminatedListener}
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.BaseDataReader.SleepingPolicy
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.{EvaluationCallback, WorksheetCompilerResult}
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinter

import scala.util.control.NonFatal

private object WorksheetCompilerLocalEvaluator {

  private val RunnerClassName = "org.jetbrains.plugins.scala.worksheet.PlainWorksheetRunner"

  // this method is only used when run type is [[org.jetbrains.plugins.scala.worksheet.server.OutOfProcessServer]]
   def executeWorksheet(file: VirtualFile, mainClassName: String, addToCp: String)
                       (callback: EvaluationCallback, worksheetPrinter: WorksheetEditorPrinter)
                       (implicit module: Module): Unit =
    invokeLater {
      try {
        val params = createJavaParameters(file, mainClassName, addToCp)
        val processHandler = createOSProcessHandler(params)

        WorksheetFileHook.updateStoppableProcess(file, Some(() => processHandler.destroyProcess()))
        processHandler.addProcessListener(new ProcessAdapter {
          override def processTerminated(event: ProcessEvent): Unit =
            WorksheetFileHook.updateStoppableProcess(file, None)
        })

        processHandler.addProcessListener(processListener(callback, worksheetPrinter))

        processHandler.startNotify()
      } catch {
        case NonFatal(ex: Throwable) =>
          callback(WorksheetCompilerResult.UnknownError(ex))
          throw ex
      }
    }

  private def createJavaParameters(originalFile: VirtualFile, mainClassName: String, addToCp: String)
                                  (implicit module: Module): JavaParameters =
    createDefaultParameters(originalFile.getCanonicalPath, mainClassName, addToCp)

  private def createDefaultParameters(originalFilePath: String, mainClassName: String, addToCp: String)
                                     (implicit module: Module): JavaParameters =
    createParameters(
      module = module,
      mainClassName = mainClassName,
      workingDirectory = Option(module.getProject.baseDir).fold("")(_.getPath),
      additionalCp = addToCp,
      worksheetField = originalFilePath
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

    params.getClassPath.addScalaCompilerClassPath(module)
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

  // originally copied from com.intellij.execution.configurations.SimpleJavaParameters.createOSProcessHandler
  private def createOSProcessHandler(params: JavaParameters): OSProcessHandler = {
    val options = new BaseOutputReader.Options() {
      // (SCL-17363) Currently we rely on that complete lines are passed to
      // org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterPlain.processLine
      // Be cautious though, it leads to outbound buffer increase if someone prints long lines
      // (see com.intellij.util.io.BaseOutputReader.myLineBuffer)
      override def sendIncompleteLines = false
      override def splitToLines = true
      override def policy: SleepingPolicy = SleepingPolicy.NON_BLOCKING
    }
    val processHandler = new OSProcessHandler(params.toCommandLine) {
      override def readerOptions(): BaseOutputReader.Options = options
    }
    ProcessTerminatedListener.attach(processHandler)
    processHandler
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
