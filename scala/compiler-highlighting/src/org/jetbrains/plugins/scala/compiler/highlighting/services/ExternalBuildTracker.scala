package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.{CompilationToken, ModuleKey}
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilationLogicService

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Records what an IDE build produced, so that a project built through Build, Rebuild or automake is known to
 * [[CompilationLogicService]].
 *
 * A build reports one target at a time, so the session is accumulated across those reports and committed at
 * the end.
 */
@Service(Array(Service.Level.PROJECT))
final class ExternalBuildTracker(project: Project) {

  import ExternalBuildTracker.Session

  private val session = new AtomicReference(Option.empty[Session])
  private val logic = CompilationLogicService(project)

  def buildStarted(id: UUID): Unit =
    session.set(Some(Session(id, logic.compilationStarted(Set.empty))))

  /** One target of the running build finished, having compiled `sources`. */
  def targetBuilt(target: ModuleKey, sources: Set[VirtualFile]): Unit =
    session.updateAndGet(_.map(_.withTarget(target, sources)))

  /**
   * The running build is a rebuild, so it began by deleting every target's output.t
   * That is a change to everything the compilers read.
   */
  def rebuildStarted(): Unit = 
    val before = session.getAndUpdate(_.map(_.copy(outputsWiped = true)))
    if (before.exists(!_.outputsWiped)) {
      logic.invalidateAll()
      session.updateAndGet(_.map(_.copy(token = logic.compilationStarted(Set.empty))))
    }
  

  /**
   * The build ended. Nothing is recorded unless it ended without errors: a build that failed part way still
   * reports the targets it got through, and telling them apart from the one that failed is not something the
   * per-target reports currently support.
   */
  def buildFinished(id: UUID, successful: Boolean): Unit =
    session.getAndSet(None).filter(_.id == id).filter(_ => successful).foreach { finished =>
      logic.compilationSucceeded(finished.token, finished.targets, finished.sources)
    }
}

object ExternalBuildTracker {

  private final case class Session(id: UUID,
                                   token: CompilationToken,
                                   targets: Set[ModuleKey] = Set.empty,
                                   sources: Set[VirtualFile] = Set.empty,
                                   outputsWiped: Boolean = false) {

    def withTarget(target: ModuleKey, compiled: Set[VirtualFile]): Session =
      copy(targets = targets + target, sources = sources ++ compiled)
  }

  def apply(project: Project): ExternalBuildTracker =
    project.getService(classOf[ExternalBuildTracker])
}
