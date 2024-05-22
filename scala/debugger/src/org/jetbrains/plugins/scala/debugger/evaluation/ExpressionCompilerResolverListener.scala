package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, ThrowableComputable}
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.ExpressionCompilerResolverListener.ExpressionCompilers
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.{DependencyManager, ScalaVersion}

import java.nio.file.Path
import scala.util.control.NonFatal

private final class ExpressionCompilerResolverListener(project: Project) extends DebuggerManagerListener {

  override def sessionCreated(session: DebuggerSession): Unit = {
    if (project.isDisposed) return

    val resolve: ThrowableComputable[Map[ScalaVersion, Path], Exception] = () => {
      project
        .modulesWithScala
        .flatMap(_.scalaMinorVersion)
        .filter(_.isScala3)
        .flatMap { v =>
          resolveExpressionCompilerJar(v) match {
            case Some(jar) => Some(v -> jar)
            case None => None
          }
        }
        .toMap
    }

    val expressionCompilers = try {
      ProgressManager
        .getInstance()
        .runProcessWithProgressSynchronously(
          resolve,
          DebuggerBundle.message("resolving.expression.compiler"),
          true,
          project
        )
    } catch {
      case _: ProcessCanceledException => Map.empty[ScalaVersion, Path]
      case NonFatal(_) => Map.empty[ScalaVersion, Path]
    }

    project.putUserData(ExpressionCompilers, expressionCompilers)
  }

  private def resolveExpressionCompilerJar(scalaVersion: ScalaVersion): Option[Path] = {
    val dep = "ch.epfl.scala" % s"scala-expression-compiler_${scalaVersion.minor}" % "4.0.4"
    DependencyManager.resolveSafe(dep).toOption.flatMap(_.headOption).map(_.file.toPath)
  }
}

private object ExpressionCompilerResolverListener {
  final val ExpressionCompilers: Key[Map[ScalaVersion, Path]] = Key.create("scala_debugger_expression_compilers")
}
