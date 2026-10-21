package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import org.jetbrains.plugins.scala.compiler.highlighting.services.core.BlockedTargets.Block

/**
 * For each target whose last failed compilation reported errors only in other targets, those targets.
 *
 */
final case class BlockedTargets(byTarget: Map[ModuleKey, Block]):
  /** The targets that blocked the last failed compilation of `target`; empty when it was not blocked. */
  def blockersOf(target: Option[ModuleKey]): Set[ModuleKey] =
    target.flatMap(byTarget.get).fold(Set.empty)(_.blockers)

  /**
   * The blockers of `target` that may still be blocking it: those with no file modified since the compilation
   * that found them was dispatched. `modifiedAfter(epoch)` is the targets with a file modified after `epoch`.
   *
   * An edit to a blocker does not unblock anything, since it may not be the fix, but it does mean the failure no
   * longer describes that blocker's sources, so nothing should be shown from it.
   */
  def currentBlockersOf(target: Option[ModuleKey],
                        modifiedAfter: Long => Set[ModuleKey]): Set[ModuleKey] =
    target.flatMap(byTarget.get).fold(Set.empty) { block =>
      block.blockers -- modifiedAfter(block.asOf)
    }

  /**
   * Records what a failed compilation says about blocking.
   *
   * Only a compilation of a single target can say that the target was blocked. In a compilation of several, a
   * target with nothing to compile reports nothing, exactly like one the build never reached, so nothing is
   * recorded for any of them and what was known about them is dropped. The next compilation of such a target
   * is a single-target one, because the failed files are stripped from later scopes.
   *
   * The single target is blocked when errors were reported, none of them against it, and none of its files was
   * reached.
   */
  def failed(token: CompilationToken, failure: FailedCompilation): BlockedTargets =
    val requested = failure.requestedTargets
    if (requested.size != 1) BlockedTargets(byTarget -- requested)
    else
      val target = requested.head
      val blockers = failure.erroredTargets
      val isBlocked = blockers.nonEmpty && !blockers.contains(target) && !failure.coveredTargets.contains(target)
      if (isBlocked)
        BlockedTargets(byTarget + (target -> Block(blockers, token.epoch)))
      else
        BlockedTargets(byTarget - target)

  /** `targets` were built: none of them is blocked, and none of them blocks anything any more. */
  def succeeded(targets: Set[ModuleKey]): BlockedTargets =
    BlockedTargets((byTarget -- targets).collect {
      case (target, block) if !block.blockers.subsetOf(targets) =>
        target -> block.copy(blockers = block.blockers -- targets)
    })


  override def toString: String =
    byTarget.map((target, block) =>
        s"${target.module.getName} <- ${block.blockers.map(_.module.getName).mkString(",")}@${block.asOf}")
      .mkString("[", "; ", "]")

object BlockedTargets:
  val empty: BlockedTargets = BlockedTargets(Map.empty)

  /**
   * The targets one compilation was blocked by, and the epoch it was dispatched at: what it found holds for the
   * sources as they were then.
   */
  final case class Block(blockers: Set[ModuleKey], asOf: Long)