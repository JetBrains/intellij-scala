package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationDecision.{Document, Incremental, Recorded, UpToDate}

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
 * @param coveredFiles     Files reached and processed during the last compilation attempt.
 * @param failedScopes     Exact scopes (combinations of files) that failed during recent attempts.
 */
final case class CompilationState(
  changesEpoch: Long,
  lastBuildEpoch: Long,
  lastAttemptEpoch: Long,
  compiledAt: Map[ModuleKey, Long],
  modifiedAt: Map[VirtualFile, Long],
  failedFiles: Set[VirtualFile],
  coveredFiles: Set[VirtualFile],
  failedScopes: Set[Set[VirtualFile]]
):
  /** The set of modified files that have not yet been successfully built. */
  def modifiedFiles: Set[VirtualFile] = modifiedAt.keySet

  override def toString: String = s"changes:$changesEpoch, lastBuild:$lastBuildEpoch, " +
    s"lastAttempt:$lastAttemptEpoch, " +
    s"\n compiledAt:$compiledAt" +
    s"\n modifiedAt: ${modifiedAt.map((f, at) => s"${f.getPresentableName}@$at").mkString(",")}" +
    s"\n failedFiles: ${failedFiles.map(_.getPresentableName).mkString(",")}" +
    s"\n coveredFiles: ${coveredFiles.map(_.getPresentableName).mkString(",")}" +
    s"\n failedScopes: ${failedScopes.map(_.map(_.getPresentableName).mkString("[", ",", "]")).mkString(",")}"

object CompilationState:

  /** The initial state where no files are built or relied upon. */
  val empty: CompilationState = CompilationState(0L, 0L, 0L, Map.empty, Map.empty, Set.empty, Set.empty, Set.empty)

  extension (state: CompilationState)

    /**
     * Records modifications to the specified files, advancing the change epoch and marking them as unbuilt.
     * Clears any previous failure status and obsolete failed scopes for these files.
     */
    def modified(files: Set[VirtualFile]): CompilationState =
      val epoch = state.changesEpoch + 1
      state.copy(
        changesEpoch = epoch,
        modifiedAt = state.modifiedAt.filter(_._1.isValid) ++ files.filter(_.isValid).map(_ -> epoch),
        failedFiles = state.failedFiles -- files,
        failedScopes = Set.empty
      )

    /**
     * Invalidates all compilation outputs due to fundamental project changes (e.g., roots, libraries, or SDK).
     * Resets all module states and tracking sets.
     */
    def invalidated: CompilationState =
      state.copy(changesEpoch = state.changesEpoch + 1, compiledAt = Map.empty,
        modifiedAt = Map.empty, failedFiles = Set.empty, coveredFiles = Set.empty, failedScopes = Set.empty)

    /**
     * Records a successful compilation attempt.
     * Updates the build epochs for the specified targets, clears failed files and scopes,
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
        coveredFiles = covered.filter(_.isValid),
        failedScopes = Set.empty
      )

    /**
     * Records a failed compilation attempt.
     * Appends the requested files to the failed set and records the exact scope that failed.
     * Replaces the covered files for the current attempt. No successful build epoch is recorded.
     */
    def failed(token: CompilationToken, covered: Set[VirtualFile]): CompilationState =
      state.copy(
        failedFiles = (state.failedFiles ++ token.files).filter(_.isValid),
        lastAttemptEpoch = token.epoch,
        coveredFiles = covered.filter(_.isValid),
        failedScopes = state.failedScopes + token.files.filter(_.isValid)
      )

    /** Creates a snapshot token for a compilation request spanning the specified files. */
    def tokenFor(files: Set[VirtualFile]): CompilationToken =
      CompilationToken(state.changesEpoch, files)

    /** Determines the required compilation strategy to obtain up-to-date diagnostics for a specific file. */
    def decide(target: Option[ModuleKey], file: VirtualFile): CompilationDecision =
      (target, file) match
        case (t, _) if state.isCurrent(t) && state.modifiedFiles.isEmpty => UpToDate
        case (_, f) if state.isRecorded(f) => Recorded
        case (t, f) if state.isDocumentCompilable(t, f) => Document
        case (_, f) =>
          val scope = state.incrementalScope(f)
          if (state.isBlocked(scope)) UpToDate
          else Incremental(scope)

    /** Checks if the file was covered in the most recent compilation attempt and remains unmodified since. */
    private def isRecorded(file: VirtualFile): Boolean =
      state.lastAttemptEpoch == state.changesEpoch && state.coveredFiles.contains(file)

    /**
     * Checks if we recently tried to compile this exact set of files and it failed,
     * and no files in the project have been modified since that attempt.
     */
    private def isBlocked(scope: Set[VirtualFile]): Boolean =
      state.lastAttemptEpoch == state.changesEpoch && state.failedScopes.contains(scope)

    /** Calculates the scope for incremental compilation, excluding known unbuildable files. */
    private def incrementalScope(file: VirtualFile): Set[VirtualFile] =
      (state.modifiedFiles -- state.failedFiles + file).filter(_.isValid)

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