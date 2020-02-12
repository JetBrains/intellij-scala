package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.{compilerVersion, containsDotty}
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}

import scala.util.Try

/**
 * Downloads using [[DependencyManager]] dotty-sbt-bridges and saves path.
 * to file to the [[org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings]].
 */
class DownloadDottySbtBridgeTask
  extends CompileTask {

  import DownloadDottySbtBridgeTask._

  override def execute(context: CompileContext): Boolean = {
    val errors = for {
      module <- context.getProject.modules
      if !module.isBuildModule
      dottyVersion <- getDottyVersion(module)
      error <- resolveDottySbtBridge(module, dottyVersion).left.toOption
    } yield error
    errors.foreach(context.addMessage(CompilerMessageCategory.ERROR, _, null, -1, -1))
    errors.isEmpty
  }
}

object DownloadDottySbtBridgeTask {

  private def getDottyVersion(module: Module): Option[String] = {
    val compilerClasspath = module.scalaCompilerClasspath
    if (containsDotty(compilerClasspath)) {
      val urls = compilerClasspath.map(_.toURI.toURL).toSet
      compilerVersion(urls)
    } else {
      None
    }
  }

  private def resolveDottySbtBridge(module: Module, dottyVersion: String): Either[String, Unit] = {
    val descriptor = "ch.epfl.lamp" % "dotty-sbt-bridge" % dottyVersion
    Try(DependencyManager.resolveSingle(descriptor))
      .toEither
      .map { resolvedDependency =>
        val dottySbtBridgePath = resolvedDependency.file.canonicalPath
        val profile = module.scalaCompilerSettingsProfile
        val settings = profile.getSettings
        if (settings.dottySbtBridgePath != dottySbtBridgePath)
          profile.setSettings(settings.copy(dottySbtBridgePath = dottySbtBridgePath))
      }
      .left.map { error => s"Error resolving $descriptor. $error" }
  }
}