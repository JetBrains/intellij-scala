package org.jetbrains.plugins.scala
package worksheet.server

import java.io._
import java.net._

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.progress.CompilerTask
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompilerPaths}
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.Base64Converter
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.DummyClient
import org.jetbrains.jps.incremental.scala.remote._
import org.jetbrains.plugins.scala.compiler.{ErrorHandler, NonServerRunner, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettings}
import org.jetbrains.plugins.scala.worksheet.actions.{RunWorksheetAction, WorksheetFileHook}
import org.jetbrains.plugins.scala.worksheet.processor.{WorksheetCompiler, WorksheetSourceProcessor}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.ReplModeArgs
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.{MyTranslatingClient, OuterCompilerInterface}
import org.jetbrains.plugins.scala.worksheet.ui.{WorksheetEditorPrinterBase, WorksheetIncrementalEditorPrinter}

/**
  * User: Dmitry Naydanov
 * Date: 1/28/14
 */
class RemoteServerConnector(psiFile: PsiFile, worksheet: File, output: File, worksheetClassName: String,
                            replArgs: Option[ReplModeArgs], needsCheck: Boolean) extends RemoteServerConnectorBase(
  RunWorksheetAction.getModuleFor(psiFile), Seq(worksheet), output, needsCheck) {
  
  val runType: WorksheetMakeType = WorksheetCompiler.getRunType(module.getProject)

  override protected def compilerSettings: ScalaCompilerSettings = {
    WorksheetCompiler.getCustomCompilerProfileName(psiFile).flatMap {
      name => 
        ScalaCompilerConfiguration.instanceIn(psiFile.getProject).customProfiles.find(_.getName == name)
    }.map(_.getSettings).getOrElse(super.compilerSettings)
  }

  /**
    * Args (for running in compile server process only)
    * 0. Compiled class name to execute 
    * 1. Path to runners.jar (needed to load MacroPrinter for types)
    * 2. Output - path to temp file, where processed worksheet code is written
    * 3. Output dir for compiled worksheet (i.e. for compiled temp file with processed code)
    * 4. Original worksheet file path. Used as id of REPL session on compile server (iff REPL enabled)
    * 5. Code chunk to interpret (iff REPL enabled)
    * 6. "replenabled" - iff/if REPL mode enabled
    */
  override val worksheetArgs: Array[String] =
    if (runType != OutOfProcessServer) {
      val base = Array(worksheetClassName, runnersJar.getAbsolutePath, output.getAbsolutePath) ++ outputDirs
      replArgs.map(ra => base ++ Array(ra.path, ra.codeChunk, "replenabled")).getOrElse(base)
    } else Array.empty[String]

  def compileAndRun(callback: Runnable, originalFile: VirtualFile, consumer: OuterCompilerInterface): ExitCode = {

    val project = module.getProject
    val worksheetHook = WorksheetFileHook.instance(project)

    val client = new MyTranslatingClient(callback, project, originalFile, consumer)

    try {
      val worksheetProcess = runType match {
        case InProcessServer | OutOfProcessServer =>
           new RemoteServerRunner(project).buildProcess(arguments, client)
        case NonServer =>
          val eventClient = new ClientEventProcessor(client)
          
          val encodedArgs = arguments map {
            case "" => Base64Converter.encode("#STUB#" getBytes "UTF-8")
            case s => Base64Converter.encode(s getBytes "UTF-8")
          }

          val errorHandler = new ErrorHandler {
            override def error(message: String): Unit = Notifications.Bus notify {
              new Notification(
                "Scala Worksheet",
                "Cannot run worksheet",
                s"<html><body>${message.replace("\n", "<br>")}</body></html>",
                NotificationType.ERROR
              )
            }
          }

          new NonServerRunner(project, Some(errorHandler)).buildProcess(encodedArgs, (text: String) => {
            val event = Event.fromBytes(Base64Converter.decode(text.getBytes("UTF-8")))
            eventClient.process(event)
          })
      }
      
      if (worksheetProcess == null) return ExitCode.ABORT

      val fileToReHighlight = extensions.inReadAction(PsiManager getInstance project findFile originalFile) match {
        case scalaFile: ScalaFile if WorksheetCompiler.isWorksheetReplMode(scalaFile) => Some(scalaFile)
        case _ => None
      }

      worksheetHook.disableRun(originalFile, Some(worksheetProcess))
      worksheetProcess.addTerminationCallback({
        worksheetHook.enableRun(originalFile, client.isCompiledWithErrors)
        fileToReHighlight foreach  WorksheetIncrementalEditorPrinter.rehighlight
      })

      WorksheetProcessManager.add(originalFile, worksheetProcess)

      worksheetProcess.run()
      
      ExitCode.OK
    } catch {
      case _: SocketException =>
        ExitCode.OK // someone has stopped the server
    } 
  }

  private def outputDirs = (ModuleRootManager.getInstance(module).getDependencies :+ module).map(
    m => CompilerPaths.getModuleOutputPath(m, false))
}

object RemoteServerConnector {
  class MyTranslatingClient(callback: Runnable, project: Project, worksheet: VirtualFile, consumer: OuterCompilerInterface) extends DummyClient {
    private val length = WorksheetSourceProcessor.END_GENERATED_MARKER.length
    
    private var hasErrors = false

    def isCompiledWithErrors: Boolean = hasErrors
    
    override def progress(text: String, done: Option[Float]) {
      consumer.progress(text, done)
    }

    override def trace(exception: Throwable) {
      consumer trace exception
    }

    override def message(kind: Kind, text: String, source: Option[File], line: Option[Long], column: Option[Long]) {
      val lines = text split "\n"
      val linesLength = lines.length

      val differ = if (linesLength > 2) {
        val i = lines(linesLength - 2) indexOf WorksheetSourceProcessor.END_GENERATED_MARKER
        if (i > -1) i + length else 0
      } else 0
      
      val finalText = if (differ == 0) text else {
        val buffer = new StringBuilder
        
        for (j <- 0 until (linesLength - 2)) buffer append lines(j) append "\n"

        val lines1 = lines(linesLength - 1)

        buffer append lines(linesLength - 2).substring(differ) append "\n" append (
          if (lines1.length > differ) lines1.substring(differ) else lines1) append "\n"
        buffer.toString()
      }
      
      val line1 = line.map(i => i - 4).map(_.toInt)
      val column1 = column.map(_ + 1 - differ).map(_.toInt)

      import BuildMessage.Kind._

      val category = kind match {
        case INFO | JPS_INFO | OTHER =>
          CompilerMessageCategory.INFORMATION
        case ERROR =>
          hasErrors = true
          CompilerMessageCategory.ERROR
        case PROGRESS =>
          CompilerMessageCategory.STATISTICS
        case WARNING =>
          CompilerMessageCategory.WARNING
      }
      
      consumer.message(
        new CompilerMessageImpl(project, category, finalText, worksheet, line1 getOrElse -1, column1 getOrElse -1, null)
      )
    }

    override def compilationEnd() {
      if (!hasErrors) callback.run()
    }

    override def worksheetOutput(text: String) {
      consumer.worksheetOutput(text)
    }
  }
  
  trait OuterCompilerInterface {
    def message(message: CompilerMessageImpl)
    def progress(text: String, done: Option[Float])
    
    def worksheetOutput(text: String)
    def trace(thr: Throwable)
  }
  
  class CompilerInterfaceImpl(task: CompilerTask, worksheetPrinter: WorksheetEditorPrinterBase,
                              indicator: Option[ProgressIndicator], auto: Boolean = false) extends OuterCompilerInterface {
    override def progress(text: String, done: Option[Float]) {
      if (auto) return
      val taskIndicator = ProgressManager.getInstance().getProgressIndicator
      
      if (taskIndicator != null) {
        taskIndicator setText text
        done foreach (d => taskIndicator.setFraction(d.toDouble))
      }
    }

    override def message(message: CompilerMessageImpl) {
      if (auto) return
      task addMessage message
    }

    override def worksheetOutput(text: String) {
      worksheetPrinter.processLine(text)
    }

    override def trace(thr: Throwable) {
      worksheetPrinter internalError s"${thr.getMessage}\n${thr.getStackTrace mkString "\n"}"
    }
  }
}
