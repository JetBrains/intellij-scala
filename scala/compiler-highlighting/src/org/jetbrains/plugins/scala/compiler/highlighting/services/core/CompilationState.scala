package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationDecision.{Document, Incremental, Recorded, UpToDate}

/**
 * Represents the current state of the build and compilation outputs.
 *
 * Tracks project-wide modifications, compilation attempts, and specific module states
 * to determine the necessary compilation strategy for individual files.
 *
 * @param changesEpoch     Increments on project-wide modifications.
 * @param lastBuildEpoch   The epoch of the most recent successful compilation.
 * @param lastAttemptEpoch The epoch of the most recent compilation attempt (success or failure).
 * @param compiledAt       Maps each module target to the epoch it was last successfully built.
 * @param modifiedAt       Maps unbuilt files to the epoch they were modified.
 * @param failedFiles      Files that failed during a recent compilation attempt.
 * @param failedTargets    Module targets that deterministically failed during a 1-target compilation.
 * @param coveredFiles     Files reached and processed during the last compilation attempt.
 */
final case class CompilationState(
  changesEpoch: Long,
  lastBuildEpoch: Long,
  lastAttemptEpoch: Long,
  compiledAt: Map[ModuleKey, Long],
  modifiedAt: Map[VirtualFile, Long],
  failedFiles: Set[VirtualFile],
  failedTargets: Set[ModuleKey],
  coveredFiles: Set[VirtualFile]
):
  /** The set of modified files that have not yet been successfully built. */
  def modifiedFiles: Set[VirtualFile] = modifiedAt.keySet

  override def toString: String = s"changes:$changesEpoch, lastBuild:$lastBuildEpoch, " +
    s"lastAttempt:$lastAttemptEpoch, " +
    s"\n compiledAt:$compiledAt" +
    s"\n modifiedAt: ${modifiedAt.map((f, at) => s"${f.getPresentableName}@$at").mkString(",")}" +
    s"\n failedFiles: ${failedFiles.map(_.getPresentableName).mkString(",")}" +
    s"\n failedTargets: $failedTargets" +
    s"\n coveredFiles: ${coveredFiles.map(_.getPresentableName).mkString(",")}"

object CompilationState:

  /** The initial state where no files are built or relied upon. */
  val empty: CompilationState = CompilationState(0L, 0L, 0L, Map.empty, Map.empty, Set.empty, Set.empty, Set.empty)

  extension (state: CompilationState)

    /**
     * Records modifications to the specified files, advancing the change epoch and marking them as unbuilt.
     * Clears any previous file failure status and immediately lifts the module quarantine.
     */
    def modified(files: Set[VirtualFile]): CompilationState =
      val epoch = state.changesEpoch + 1
      state.copy(
        changesEpoch = epoch,
        modifiedAt = state.modifiedAt.filter(_._1.isValid) ++ files.filter(_.isValid).map(_ -> epoch),
        failedFiles = state.failedFiles -- files,
        failedTargets = Set.empty
      )

    /**
     * Invalidates all compilation outputs due to fundamental project changes (e.g., roots, libraries, or SDK).
     * Resets all module states and tracking sets.
     */
    def invalidated: CompilationState =
      state.copy(changesEpoch = state.changesEpoch + 1, compiledAt = Map.empty,
        modifiedAt = Map.empty, failedFiles = Set.empty, failedTargets = Set.empty, coveredFiles = Set.empty)

    /**
     * Records a successful compilation attempt.
     * Updates the build epochs for the specified targets, clears failed files/targets,
     * and records the files covered. Drops modifications for files up to the point the token was created.
     */
    def succeeded(token: CompilationToken,
                  targets: Set[ModuleKey],
                  covered: Set[VirtualFile]): CompilationState =
      state.copy(
        lastBuildEpoch = state.lastBuildEpoch.max(token.epoch),
        lastAttemptEpoch = token.epoch,
        compiledAt = state.compiledAt ++ targets.map(_ -> token.epoch),
        modifiedAt = state.modifiedAt.filter((file, at) => at > token.epoch && file.isValid),
        failedFiles = Set.empty,
        failedTargets = Set.empty,
        coveredFiles = covered.filter(_.isValid)
      )

    /**
     * Records a failed compilation attempt.
     * Appends the requested files to the failed set. If the failure is isolated to a single target,
     * that target is quarantined. Replaces the covered files for the current attempt.
     *
     * A failure speaks only for the sources as they were when it was dispatched, as a success does. A file
     * modified since then is not marked failed, because the edit may well be the fix, and nothing is quarantined
     * once anything has changed, because the quarantine holds only while the same request would fail the same way.
     */
    def failed(token: CompilationToken, targets: Set[ModuleKey], covered: Set[VirtualFile]): CompilationState =
      val unchangedSinceDispatch = token.epoch == state.changesEpoch
      val newFailedTargets =
        if (unchangedSinceDispatch && targets.size == 1) state.failedTargets ++ targets else state.failedTargets
      val failedAsDispatched = token.files.filterNot(file => state.modifiedAt.get(file).exists(_ > token.epoch))
      state.copy(
        failedFiles = (state.failedFiles ++ failedAsDispatched).filter(_.isValid),
        failedTargets = newFailedTargets,
        lastAttemptEpoch = token.epoch,
        coveredFiles = covered.filter(_.isValid)
      )

    /** Creates a snapshot token for a compilation request spanning the specified files. */
    def tokenFor(files: Set[VirtualFile]): CompilationToken =
      CompilationToken(state.changesEpoch, files)

    /** Determines the required compilation strategy to obtain up-to-date diagnostics for a specific file. */
    def decide(target: Option[ModuleKey], file: VirtualFile): CompilationDecision =
      (target, file) match
        case (t, _) if state.isClean(t) => UpToDate
        case (_, f) if state.isRecorded(f) => Recorded
        case (t, _) if t.exists(state.failedTargets.contains) => UpToDate
        case (t, f) if state.isDocumentCompilable(t, f) => Document
        case (_, f) => Incremental(state.incrementalScope(Set(f)))

    /** Checks if the target is fully up to date with no modified files in the project. */
    private def isClean(target: Option[ModuleKey]): Boolean =
      state.isCurrent(target) && state.modifiedFiles.isEmpty

    /** Checks if the file was covered in the most recent compilation attempt and remains unmodified since. */
    private def isRecorded(file: VirtualFile): Boolean =
      state.lastAttemptEpoch == state.changesEpoch && state.coveredFiles.contains(file)

    /**
     * Calculates the scope for incremental compilation, excluding known unbuildable files.
     *
     * Takes a set because several files can be asked about at once, and the union of their scopes has to be
     * computed here rather than by folding the single-file answers.
     */
    def incrementalScope(files: Set[VirtualFile]): Set[VirtualFile] =
      (state.modifiedFiles -- state.failedFiles ++ files).filter(_.isValid)

    /** Identifies module targets eligible for document compilation based on the latest successful build epoch. */
    def documentEnabledFor: Set[ModuleKey] =
      state.compiledAt.collect {
        case (target, builtAt) if builtAt == state.lastBuildEpoch => target
      }.toSet

    /** Checks if the target is fully up to date with the latest changes. */
    private def isCurrent(target: Option[ModuleKey]): Boolean =
      target.flatMap(state.compiledAt.get).contains(state.changesEpoch)

    /** Determines if a file can be compiled via the document compiler. */
    private def isDocumentCompilable(target: Option[ModuleKey], file: VirtualFile): Boolean =
      target.flatMap(state.compiledAt.get).contains(state.lastBuildEpoch) && state.modifiedFiles == Set(file)