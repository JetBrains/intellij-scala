package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.tracing.BuildReason
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.ModuleKey
import org.jetbrains.plugins.scala.compiler.highlighting.services.ExternalBuildTracker
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.PathExt

/**
 * Feeds the per-target reports of an IDE build to [[ExternalBuildTracker]].
 *
 * A `compilationUnitId` names the target the report is about, and only an IDE build supplies one: the
 * compile server this module drives reports a whole request at once and leaves it empty. So the field also
 * tells the two apart.
 */
private final class ExternalBuildStateListener(project: Project) extends CompilerEventListener:

  private val tracker = ExternalBuildTracker(project)

  private val logger = Logger.getInstance(classOf[ExternalBuildStateListener])

  /** The reason an IDE build reports for itself when its scope forces the targets to be rebuilt. */
  private val rebuildReason: String = BuildReason.Rebuild.toString

  override def eventReceived(event: CompilerEvent): Unit = event match
    case CompilerEvent.CompilationStarted(_, Some(_), Some(reason), _) if reason == rebuildReason =>
      // Reported per target
      tracker.rebuildStarted()
    case CompilerEvent.CompilationFinished(_, Some(unitId), sources) =>
      // The report names every source of the target it built.
      val compiled = sources.flatMap(_.toPath.toVirtualFile)
      ModuleKey.of(project, unitId) match
        case Some(target) => tracker.targetBuilt(target, compiled.filter(_.isValid))
        case None => logger.warn(s"No module resolves ${unitId.moduleId}" +
          s"${if unitId.testScope then " (test)" else ""}, so what this build compiled is not recorded")
    case _ => ()

