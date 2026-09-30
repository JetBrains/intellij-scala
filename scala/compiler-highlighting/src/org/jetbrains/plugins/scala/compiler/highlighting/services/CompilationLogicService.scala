package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.{CompilationDecision, CompilationState, CompilationToken, ModuleKey}

import java.util.concurrent.atomic.AtomicReference

/**
 * Owns what the plugin knows about the state of the build, and answers the one question the triggers ask:
 * what has to be compiled for a file.
 */
@Service(Array(Service.Level.PROJECT))
final class CompilationLogicService(project: Project) {

  private val state = new AtomicReference(CompilationState.empty)
  private val Log = Logger.getInstance(classOf[CompilationLogicService])
  
  /**
   * Records that `file`, no longer match what was built from it.
   * A deletion, a move and a rename are modifications like any other.
   */
  def fileModified(file: VirtualFile): Unit = filesModified(Set(file))
  /**
   * Records that `files`, no longer match what was built from them.
   * A deletion, a move and a rename are modifications like any other.
   */
  def filesModified(files: Set[VirtualFile]): Unit = {
    Log.debug(s"File modified ${files.toSeq.toString()}")
    if (files.nonEmpty) update(_.modified(files))
  }

  /**
   * Records a change to a source the build compiles but no scope of ours can hold — a Kotlin source or
   * a template that generates Scala. What the build produced from it is stale, and only a build can repair it.
   */
  def buildInputChanged(): Unit = invalidateAll()

  /** Records that no target's output can be relied upon, e.g. after a change of roots, libraries or SDK. */
  def invalidateAll(): Unit = update(_.invalidated)

  /** Snapshots the state a compilation over `files` is about to run against.
   *  The token should be later used to either call [[compilationSucceeded()]] or [[compilationFailed()]]
   * */
  def compilationStarted(files: Set[VirtualFile]): CompilationToken = state.get().tokenFor(files)

  /**
   * Records a compilation that the compiler reported no errors for, which built `targets`.
   *
   * A compilation that throws — a dead compile server, a cancellation — is neither this nor
   * [[compilationFailed]]: it says nothing about whether the sources build.
   *
   * `covered` is the files the compiler reported compiling, which is not the same as the request's scope.
   */
  def compilationSucceeded(token: CompilationToken,
                           targets: Set[ModuleKey],
                           covered: Set[VirtualFile]): Unit =
    update(_.succeeded(token, targets, covered))

  /**
   * Records that the compiler reported errors for the compilation dispatched at `token`, so its scope is not
   * currently buildable.
   *
   * `covered` is the files the compiler reached before giving up, which is the part of the failure that is
   * still informative: the rest of the scope was never looked at.
   */
  def compilationFailed(token: CompilationToken, covered: Set[VirtualFile]): Unit =
    update(_.failed(token, covered))

  /** What has to be done to obtain up-to-date diagnostics for `file`. */
  def decide(file: VirtualFile): CompilationDecision = {
    val decision = state.get().decide(ModuleKey.of(project, file), file)
    Log.debug(s"decision: $decision -> ${state.get().modifiedFiles.map(_.getPresentableName).mkString(",")}")
    decision
  }

  /** Whether `file` is unbuilt */
  def isModified(file: VirtualFile): Boolean = state.get().modifiedFiles.contains(file)

  @TestOnly
  private[highlighting] def snapshot: CompilationState = state.get()

  private def update(transition: CompilationState => CompilationState): Unit = { 
    val update = state.updateAndGet(transition(_))
    Log.debug(s"Updating highlighting state: $update")
  }
}

object CompilationLogicService :
  def apply(project: Project): CompilationLogicService =
    project.getService(classOf[CompilationLogicService])

