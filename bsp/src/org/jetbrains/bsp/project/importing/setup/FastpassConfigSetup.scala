package org.jetbrains.bsp.project.importing.setup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.{BufferedReader, File, InputStreamReader}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.bsp.BspErrorMessage
import org.jetbrains.bsp.project.importing.FastpassProjectImportProvider
import org.jetbrains.bsp.project.importing.setup.FastpassConfigSetup.FastpassProcessCheckTimeout
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter
import scala.util.{Failure, Success, Try}

object FastpassConfigSetup {
  private val FastpassProcessCheckTimeout = 100.millis

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
      case Some(pantsRoot) =>
        val relativeDir = pantsRoot.toNioPath.relativize(baseDirVFile.toNioPath)
        val processBuilder = new ProcessBuilder(
          "fastpass",
          "create",
          s"--name=${bspWorkspace.getFileName}",
          relativeDir.toString + "::"
        )
        processBuilder.directory(new File(pantsRoot.toNioPath.toString))
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
    while(!process.waitFor(FastpassProcessCheckTimeout.length, FastpassProcessCheckTimeout.unit)){
      stdoutReader.lines().forEach(reporter.info(_, None))
      stderrReader.lines().forEach(reporter.error(_, None))
      if(cancellationFlag.get()){
        process.destroy()
      }
    }

    if(process.exitValue() == 1) {
      Success(BuildMessages.empty.status(BuildMessages.OK))
    } else {
      Failure(BspErrorMessage(s"Command ${processBuilder.command.asScala}"))
    }
  }

  override def run(implicit reporter: BuildReporter): Try[BuildMessages] = {
    reporter.start()
    val process = processBuilder.start()
    val result = waitFinish(process, reporter)
    result match {
      case Failure(err) => reporter.finishWithFailure(err)
      case Success(bm) => reporter.finish(bm)
    }
    result
  }
}
