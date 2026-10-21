package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Nested, Tag, Test}

/** The rules of [[BlockedTargets]] alone, without a [[CompilationState]]. */
@Tag(CompilerHighlightingCoreTest)
class BlockedTargetsTest extends CompilationStateTestBase:

  private val root = module("root")
  private val lib = module("lib")
  private val other = module("other")

  private val token = CompilationToken(epoch = 5L, files = Set.empty)

  private def failure(requested: Set[ModuleKey],
                      erroredIn: Set[ModuleKey],
                      reached: Set[ModuleKey] = Set.empty): FailedCompilation =
    FailedCompilation(covered = Set.empty, requested, erroredIn, reached)

  private def blockedBy(blockers: ModuleKey*): BlockedTargets =
    BlockedTargets.empty.failed(token, failure(Set(root), blockers.toSet))

  @Nested
  class Failed:

    @Test
    def errorsOnlyInADependencyBlockTheTarget(): Unit =
      assertEquals(Set(lib), blockedBy(lib).blockersOf(Some(root)))

    @Test
    def everyTargetWithErrorsIsABlocker(): Unit =
      assertEquals(Set(lib, other), blockedBy(lib, other).blockersOf(Some(root)))

    @Test
    def errorsInTheTargetItselfDoNotBlockIt(): Unit =
      assertTrue(blockedBy(root, lib).blockersOf(Some(root)).isEmpty)

    @Test
    def aTargetTheCompilerReachedIsNotBlocked(): Unit =
      val reached = BlockedTargets.empty.failed(token, failure(Set(root), Set(lib), reached = Set(root)))
      assertTrue(reached.blockersOf(Some(root)).isEmpty)

    @Test
    def aFailureWithNoErrorAgainstAnyTargetBlocksNothing(): Unit =
      assertEquals(BlockedTargets.empty, blockedBy())

    @Test
    def aFailureOfSeveralTargetsRecordsNothing(): Unit =
      val several = BlockedTargets.empty.failed(token, failure(Set(root, other), Set(lib)))
      assertEquals(BlockedTargets.empty, several)

    @Test
    def aFailureOfSeveralTargetsForgetsWhatWasKnownAboutThem(): Unit =
      val several = blockedBy(lib).failed(token, failure(Set(root, other), Set(lib)))
      assertTrue(several.blockersOf(Some(root)).isEmpty)

    @Test
    def aFailureOfTheTargetOnItsOwnReplacesItsBlockers(): Unit =
      assertTrue(blockedBy(lib).failed(token, failure(Set(root), Set(root))).blockersOf(Some(root)).isEmpty)

    @Test
    def otherTargetsAreLeftAlone(): Unit =
      val otherBlocked = BlockedTargets.empty.failed(token, failure(Set(other), Set(lib)))
      assertEquals(Set(lib), otherBlocked.failed(token, failure(Set(root), Set(root))).blockersOf(Some(other)))

  @Nested
  class Succeeded:

    @Test
    def buildingTheTargetUnblocksIt(): Unit =
      assertTrue(blockedBy(lib).succeeded(Set(root)).blockersOf(Some(root)).isEmpty)

    @Test
    def buildingTheBlockerUnblocksTheTarget(): Unit =
      assertEquals(BlockedTargets.empty, blockedBy(lib).succeeded(Set(lib)))

    @Test
    def buildingOneBlockerKeepsTheOthers(): Unit =
      assertEquals(Set(other), blockedBy(lib, other).succeeded(Set(lib)).blockersOf(Some(root)))

    @Test
    def buildingAnUnrelatedTargetChangesNothing(): Unit =
      assertEquals(blockedBy(lib), blockedBy(lib).succeeded(Set(other)))

  @Nested
  class CurrentBlockers:

    private def modifiedAfter(edited: Map[ModuleKey, Long]): Long => Set[ModuleKey] =
      epoch => edited.collect { case (target, at) if at > epoch => target }.toSet

    @Test
    def aBlockerNotEditedSinceStillBlocks(): Unit =
      assertEquals(Set(lib), blockedBy(lib).currentBlockersOf(Some(root), modifiedAfter(Map.empty)))

    @Test
    def aBlockerEditedSinceNoLongerBlocks(): Unit =
      assertTrue(blockedBy(lib).currentBlockersOf(Some(root), modifiedAfter(Map(lib -> (token.epoch + 1)))).isEmpty)

    @Test
    def aBlockerLastEditedBeforeTheCompilationStillBlocks(): Unit =
      assertEquals(Set(lib), blockedBy(lib).currentBlockersOf(Some(root), modifiedAfter(Map(lib -> token.epoch))))

    @Test
    def anEditToTheTargetItselfChangesNothing(): Unit =
      assertEquals(Set(lib), blockedBy(lib).currentBlockersOf(Some(root), modifiedAfter(Map(root -> (token.epoch + 1)))))

    @Test
    def onlyTheEditedBlockerIsLeftOut(): Unit =
      val edited = modifiedAfter(Map(lib -> (token.epoch + 1)))
      assertEquals(Set(other), blockedBy(lib, other).currentBlockersOf(Some(root), edited))

    @Test
    def anEditedBlockerIsStillRecorded(): Unit =
      val edited = blockedBy(lib)
      edited.currentBlockersOf(Some(root), modifiedAfter(Map(lib -> (token.epoch + 1))))
      assertEquals(Set(lib), edited.blockersOf(Some(root)))

  @Test
  def aTargetOutsideEveryModuleIsNeverBlocked(): Unit =
    assertTrue(blockedBy(lib).blockersOf(None).isEmpty)
