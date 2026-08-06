package org.jetbrains.bsp.project.importing.setup
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.bsp.project.importing.FastpassProjectImportProvider
import org.jetbrains.bsp.project.importing.setup.FastpassConfigSetup.{FastpassProcessCheckTimeout, logger}
import org.jetbrains.bsp.{BSP, BspBundle, BspErrorMessage}
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.awt.datatransfer.StringSelection
import java.io.{BufferedReader, InputStreamReader}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object FastpassConfigSetup {
  private val FastpassProcessCheckTimeout = 100.millis

  private val logger = Logger.getInstance(classOf[FastpassConfigSetup])

  val fastpassRelativePath = "fastpass/bin/fastpass"

  def computeBspWorkspace(file: Path): Path = {
    val pantsRoot = FastpassProjectImportProvider.pantsRoot(LocalFileSystem.getInstance().findFileByNioFile(file))
    val relativeDir = pantsRoot.get.toNioPath.relativize(file)
    val projectName = relativeDir.toString.replace("/", ".")
    val bspWorkspace = pantsRoot.get.getParent.toNioPath.resolve("bsp-projects").resolve(projectName)
    bspWorkspace
  }

  def create(baseDir: Path): Try[BspConfigSetup] = {
    val bspWorkspace = FastpassConfigSetup.computeBspWorkspace(baseDir)
    val baseDirVFile = LocalFileSystem.getInstance().findFileByNioFile(baseDir)
    FastpassProjectImportProvider.pantsRoot(baseDirVFile) match {
      case Some(_) if bspWorkspace.resolve(".bloop").exists =>
        Success(new FastpassConfigSetupEmpty(bspWorkspace))
      case Some(pantsRoot) =>
        val relativeDir = pantsRoot.toNioPath.relativize(baseDirVFile.toNioPath)
        val commandLine = new GeneralCommandLine(
          fastpassRelativePath,
          "create",
          s"--name=${bspWorkspace.getFileName}",
          relativeDir.toString + "::"
        ).withWorkingDirectory(pantsRoot.toNioPath)
        logger.info(s"Creating BSP configuration with '${commandLine.getCommandLineString}'")
        Success(new FastpassConfigSetup(commandLine))
      case None => Failure(new IllegalArgumentException(s"'$baseDir is not a pants directory'"))
    }
  }
}

class FastpassConfigSetupEmpty(bspWorkspace: Path) extends BspConfigSetup {
  override def cancel(): Unit = {  }

  override def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val realPath = bspWorkspace.toRealPath().toString
    val title = BspBundle.message("bsp.fastpass.notification.reused.workspace.title")
    val message = BspBundle.message("bsp.fastpass.notification.reused.workspace.message", realPath)
    val notification = BSP.NotificationGroup.createNotification(title, message, NotificationType.WARNING)
    notification.addAction(new AnAction(BspBundle.message("bsp.fastpass.notification.reused.workspace.button")) {
      override def actionPerformed(e: AnActionEvent): Unit = {
        CopyPasteManager.getInstance().setContents(new StringSelection(realPath))
      }

      override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
    })
    notification.setImportant(true)
    ApplicationManager.getApplication.invokeLater(new Runnable {
      override def run(): Unit = notification.notify(null)
    }, ModalityState.nonModal())

    Success(BuildMessages.empty)
  }
}

class FastpassConfigSetup(commandLine: GeneralCommandLine) extends BspConfigSetup {
  override def cancel(): Unit = cancellationFlag.set(true)

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  private def waitFinish(process: Process, reporter: BuildReporter): Try[BuildMessages] = {
    val stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream))
    val stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream))
    var buildMessages = BuildMessages.empty
    while(!process.waitFor(FastpassProcessCheckTimeout.length, FastpassProcessCheckTimeout.unit)){
      val stderrLines = stderrReader.lines()
      stderrLines.forEach{ line =>
        buildMessages = buildMessages.addError(line)
        reporter.log(line)
      }
      stdoutReader.lines().forEach{ line =>
        buildMessages = buildMessages.message(line)
        reporter.log(line)
      }
      if(cancellationFlag.get()){
        process.destroy()
      }
    }

    if(process.exitValue() == 0) {
      Success(buildMessages.status(BuildMessages.OK))
    } else {
      Failure(BspErrorMessage(
        s"""Command ${commandLine.getCommandLineString} failed with:
           |${buildMessages.errors.mkString("\n")}""".stripMargin))
    }
  }

  override def run(indicator: ProgressIndicator)(implicit reporter: BuildReporter): Try[BuildMessages] = {
    reporter.start()
    logger.info(s"Running '${commandLine.getCommandLineString}' in ${commandLine.getWorkDirectory}")
    val process = commandLine.createProcess()
    val result = waitFinish(process, reporter)
    result match {
      case Failure(err) => {
        // Log to ensure the error message is not lost. Current implementation of
        // reporter.finishWithFailure ignores errors
        logger.error(err)
        reporter.finishWithFailure(err)
      }
      case Success(bm) => reporter.finish(bm)
    }
    result
  }
}
