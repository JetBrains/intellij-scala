package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}

import java.nio.file.Path
import scala.util.control.NonFatal

private final class ExpressionCompilerResolverListener(project: Project) extends DebuggerManagerListener {

  import ExpressionCompilerResolverListener._

  override def sessionCreated(session: DebuggerSession): Unit = {
    if (project.isDisposed) return

    new Task.Backgroundable(project, DebuggerBundle.message("resolving.expression.compiler"), true, () => false) {
      override def run(indicator: ProgressIndicator): Unit = {
        if (project.isDisposed) return

        val expressionCompilers = try {
          project
            .modulesWithScala
            .flatMap(_.scalaMinorVersion)
            .filter(_.isScala3)
            .flatMap { v =>
              if (v.languageLevel >= ScalaLanguageLevel.Scala_3_7)
                Some(v -> ExpressionCompilerType.BuiltIn)
              else
                resolveExpressionCompilerJar(v, indicator).map(jar => v -> ExpressionCompilerType.ResolvedJar(jar))
            }.toMap
        } catch {
          case NonFatal(_) => Map.empty[ScalaVersion, ExpressionCompilerType]
        }

        project.putUserData(ExpressionCompilers, expressionCompilers)
      }
    }.queue()
  }

  private def resolveExpressionCompilerJar(scalaVersion: ScalaVersion, indicator: ProgressIndicator): Option[Path] = {
    val dep = "ch.epfl.scala" % s"scala-expression-compiler_${scalaVersion.minor}" % ScalaExpressionCompilerVersion
    val manager = new DependencyManagerBase {
      override protected def progressIndicator: Option[ProgressIndicator] = Some(indicator)
    }
    manager.resolveSafe(dep).toOption.flatMap(_.headOption).map(_.file)
  }
}

private object ExpressionCompilerResolverListener {

  sealed trait ExpressionCompilerType
  object ExpressionCompilerType {
    /**
     * Scala 3.7 ships the debugger expression compiler as part of the scala3-compiler.jar. We do not need to manually
     * resolve a separate expression compiler jar.
     */
    case object BuiltIn extends ExpressionCompilerType

    /**
     * Scala 3.0 to Scala 3.6 use a separate jar which hosts the debugger expression compiler which needs to be
     * manually resolved.
     */
    case class ResolvedJar(path: Path) extends ExpressionCompilerType
  }

  final val ExpressionCompilers: Key[Map[ScalaVersion, ExpressionCompilerType]] = Key.create("scala_debugger_expression_compilers")

  final val ScalaExpressionCompilerVersion = "4.2.8"
}
