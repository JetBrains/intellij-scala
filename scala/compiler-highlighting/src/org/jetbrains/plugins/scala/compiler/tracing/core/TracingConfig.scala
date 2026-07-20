package org.jetbrains.plugins.scala.compiler.tracing.core

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.utils.EelSystemFolderUtils
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import org.jetbrains.plugins.scala.extensions._

import java.nio.file.Path

object TracingConfig {
  
  private val TRACE_FILE_PREFIX = "scala-compiler-trace"

  lazy val isTracingEnabled: Boolean = try {
    Registry.is("scala.compiler.highlighting.tracing.enabled")
  } catch {
    case _: Throwable => false
  }

  /**
   * The file name is qualified by the project's location hash, so several projects sharing the same
   * (local) log directory don't truncate each other's trace.
   */
  def traceFilePath(project: Project): Path =
    logDirectory(project).resolve(s"$TRACE_FILE_PREFIX-${project.getName}.json")

  private def logDirectory(project: Project): Path =
    logDirectory(EelProviderUtil.getEelDescriptor(project))

  private def logDirectory(eelDescriptor: EelDescriptor): Path =
    eelDescriptor match {
      case LocalEelDescriptor.INSTANCE => localLogDirectory
      case remote => EelSystemFolderUtils.getSystemFolder(remote) / "logs"
    }

  private def localLogDirectory: Path =
    PathManager.getLogDir.toCanonicalPath
}
