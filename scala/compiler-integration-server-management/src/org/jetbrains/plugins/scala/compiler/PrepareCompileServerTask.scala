package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.server.CompileServerPort
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

private final class PrepareCompileServerTask extends ScalaCompileTask {
  import PrepareCompileServerTask.Log

  override protected def run(context: CompileContext): Boolean = {
    val project = context.getProject
    ensureCompileServerRunning(project)
    writePortNumber(force = context.isRebuild, project)
    true
  }

  override protected def presentableName: String =
    "Writing Scala Compile Server TCP port number to disk"

  override protected def log: Logger = Log

  private def ensureCompileServerRunning(project: Project): Unit = {
    if (project.isDisposed) return
    val settings = ScalaCompileServerSettings.getInstance

    val compileServerRequired = settings.COMPILE_SERVER_ENABLED && project.hasScala
    Log.debug(s"CompileServerBuildManagerListener.compileServerRequired: $compileServerRequired")
    if (compileServerRequired) {
      CompileServerLauncher.ensureServerRunning(project)
    }
    if (ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED)
      CompileServerNotificationsService.get(project).warnIfCompileServerJdkMayLeadToCompilationProblems()
  }

  private def writePortNumber(force: Boolean, project: Project): Unit = {
    val compileServerSystemDir = CompileServerLauncher.scalaCompileServerSystemDir(project)

    val currentPort = CompileServerLauncher.port
    currentPort match {
      case Some(port) if force =>
        // Unconditionally write the port number to disk.
        writePortFile(compileServerSystemDir, port)

      case Some(port) =>
        // Only write the port number to disk if the old port number differs.
        CompileServerPort.readPortFile(compileServerSystemDir) match {
          case Some(oldPort) if port != oldPort =>
            writePortFile(compileServerSystemDir, port)
          case Some(_) =>
            // The port written in the port file matches the current one. Proceed.
          case None =>
            // The port file might not exist or there was a problem with reading it. Write the new port number to disk.
            writePortFile(compileServerSystemDir, port)
        }

      case None =>
        // There is no currently running Scala Compile Server instance. Remove the port file.
        val path = CompileServerPort.portFilePath(compileServerSystemDir)
        tryDeleteIfExists(path)
    }
  }

  private def writePortFile(compileServerSystemDir: Path, port: Int): Unit = {
    import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
    val path = CompileServerPort.portFilePath(compileServerSystemDir)
    Files.writeString(path, port.toString, StandardCharsets.UTF_8, TRUNCATE_EXISTING, CREATE)
  }

  private def tryDeleteIfExists(path: Path): Unit =
    try Files.deleteIfExists(path)
    catch { case NonFatal(_) => }
}

private object PrepareCompileServerTask {
  val Log: Logger = Logger.getInstance(classOf[PrepareCompileServerTask])
}
