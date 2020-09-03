package org.jetbrains.bsp.project.importing.setup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.{BufferedReader, File, InputStreamReader}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.bsp.BspErrorMessage
import org.jetbrains.bsp.project.importing.FastpassProjectImportProvider
import org.jetbrains.bsp.project.importing.setup.FastpassConfigSetup.{FastpassProcessCheckTimeout, logger}
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter
import scala.util.{Failure, Success, Try}

object FastpassConfigSetup {
  private val FastpassProcessCheckTimeout = 100.millis

  val logger = Logger.getInstance(classOf[FastpassConfigSetup])

  def computeBspWorkspace(file: File): Path = {
    val pantsRoot = FastpassProjectImportProvider.pantsRoot(LocalFileSystem.getInstance().findFileByIoFile(file))
    val relativeDir = pantsRoot.get.toNioPath.relativize(file.toPath)
    val projectName = relativeDir.toString.replace("/", ".")
    val bspWorkspace = pantsRoot.get.getParent.toNioPath.resolve("bsp-projects").resolve(projectName)
    bspWorkspace.toFile.toPath
  }

  def create(baseDir: File): Try[FastpassConfigSetup] = {
    val bspWorkspace = FastpassConfigSetup.computeBspWorkspace(baseDir)
    val baseDirVFile = LocalFileSystem.getInstance().findFileByIoFile(baseDir)
    FastpassProjectImportProvider.pantsRoot(baseDirVFile) match {
      case Some(_) if bspWorkspace.resolve(".bloop").toFile.exists()=> {
        logger.info(s"BSP configuration already found in $bspWorkspace")
        Success(new FastpassConfigSetup(new ProcessBuilder("true")))
      }
      case Some(pantsRoot) =>
        val relativeDir = pantsRoot.toNioPath.relativize(baseDirVFile.toNioPath)
        val processBuilder = new ProcessBuilder(
          "./fastpass/bin/fastpass",
          "create",
          s"--name=${bspWorkspace.getFileName}",
          relativeDir.toString + "::"
        )
        processBuilder.directory(new File(pantsRoot.toNioPath.toString))
        logger.info(s"Creating BSP configuration with '${processBuilder.command().asScala.mkString(" ")}'")
        Success(new FastpassConfigSetup(processBuilder))
      case None => Failure(new IllegalArgumentException(s"'$baseDir is not a pants directory'"))
    }
  }
}

class FastpassConfigSetup(processBuilder: ProcessBuilder) extends BspConfigSetup {
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
        s"""Command ${processBuilder.command.asScala} failed with:
           |${buildMessages.errors.mkString("\n")}""".stripMargin))
    }
  }

  override def run(implicit reporter: BuildReporter): Try[BuildMessages] = {
    reporter.start()
    logger.info(s"Running '${processBuilder.command().asScala.mkString(" ")}' in ${processBuilder.directory()}")
    val process = processBuilder.start()
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
