package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationStateTestBase.CompilerHighlightingTests
import org.junit.jupiter.api.Assertions.{assertAll, assertEquals, assertTrue}
import org.junit.jupiter.api.{Nested, Tag, Test}

@Tag(CompilerHighlightingTests)
class CompilationStateTest extends CompilationStateTestBase {

  private val main = module("main")
  private val core = module("core")

  private val a = file("A.scala", in = main)
  private val b = file("B.scala", in = main)
  private val c = file("C.scala", in = core)

  private val empty = CompilationState.empty

  @Nested
  class Empty {

    @Test
    def knowsNothing(): Unit = assertAll(
      () => assertEquals(0L, empty.changesEpoch),
      () => assertEquals(0L, empty.lastBuildEpoch),
      () => assertEquals(0L, empty.lastAttemptEpoch),
      () => assertTrue(empty.compiledAt.isEmpty),
      () => assertTrue(empty.modifiedAt.isEmpty),
      () => assertTrue(empty.failedFiles.isEmpty),
      () => assertTrue(empty.coveredFiles.isEmpty)
    )
  }

  @Nested
  class Modified {

    @Test
    def advancesTheChangeClockByOne(): Unit =
      assertEquals(1L, empty.modified(Set(a)).changesEpoch)

    @Test
    def stampsEveryFileWithTheNewEpoch(): Unit = {
      val state = empty.modified(Set(a, b))

      assertEquals(Some(1L), state.modifiedAt.get(a))
      assertEquals(Some(1L), state.modifiedAt.get(b))
    }

    @Test
    def keepsEarlierEntriesAtTheirOwnEpoch(): Unit = {
      val state = empty.modified(Set(a)).modified(Set(b))

      assertEquals(Some(1L), state.modifiedAt.get(a))
      assertEquals(Some(2L), state.modifiedAt.get(b))
    }

    @Test
    def restampsAFileEditedAgain(): Unit =
      assertEquals(Some(2L), empty.modified(Set(a)).modified(Set(a)).modifiedAt.get(a))

    @Test
    def withdrawsTheEditedFilesFromTheFailedFiles(): Unit = {
      val failed = empty.failed(empty.startCompilation(a, b), Set(a, b))

      assertEquals(names(b), names(failed.modified(Set(a)).failedFiles))
    }

    @Test
    def ignoresFilesThatNoLongerExist(): Unit = {
      delete(a)

      assertTrue(empty.modified(Set(a)).modifiedAt.isEmpty)
    }

    @Test
    def prunesExistingEntriesForFilesThatNoLongerExist(): Unit = {
      val state = empty.modified(Set(a))
      delete(a)

      assertEquals(names(b), names(state.modified(Set(b)).modifiedFiles))
    }

    @Test
    def leavesTheBuildRecordAlone(): Unit = {
      val built = empty.succeeded(empty.startCompilation(a), Set(main), Set(a))
      val state = built.modified(Set(a))

      assertAll(
        () => assertEquals(built.compiledAt, state.compiledAt),
        () => assertEquals(built.lastBuildEpoch, state.lastBuildEpoch),
        () => assertEquals(built.lastAttemptEpoch, state.lastAttemptEpoch)
      )
    }
  }

  @Nested
  class TokenFor {

    @Test
    def capturesTheCurrentEpochAndTheScope(): Unit = {
      val token = empty.modified(Set(a)).tokenFor(Set(a, b))

      assertEquals(1L, token.epoch)
      assertEquals(names(a, b), names(token.files))
    }

    @Test
    def capturesAnEmptyScopeForABuildThatNamesNoFilesUpFront(): Unit = {
      val token = empty.modified(Set(a)).startCompilation()

      assertEquals(1L, token.epoch)
      assertTrue(token.files.isEmpty)
    }
  }

  @Nested
  class Succeeded {

    @Test
    def marksTheTargetsItBuilt(): Unit = {
      val dispatched = empty.modified(Set(a))
      val state = dispatched.succeeded(dispatched.startCompilation(a), Set(main), Set(a))

      assertEquals(Some(1L), state.compiledAt.get(main))
      assertEquals(None, state.compiledAt.get(core))
    }

    @Test
    def keepsTargetsBuiltEarlier(): Unit = {
      val first = empty.succeeded(empty.startCompilation(c), Set(core), Set(c))
      val dispatched = first.modified(Set(a))
      val second = dispatched.succeeded(dispatched.startCompilation(a), Set(main), Set(a))

      assertEquals(Some(0L), second.compiledAt.get(core))
      assertEquals(Some(1L), second.compiledAt.get(main))
    }

    @Test
    def advancesTheBuildClockToItsOwnEpoch(): Unit = {
      val dispatched = empty.modified(Set(a))

      assertEquals(1L, dispatched.succeeded(dispatched.startCompilation(a), Set(main), Set(a)).lastBuildEpoch)
    }

    @Test
    def neverPullsTheBuildClockBackwards(): Unit =
      assertEquals(2L, reportedOutOfOrder.lastBuildEpoch)

    @Test
    def assignsTheAttemptClockFromItsOwnEpoch(): Unit =
      assertEquals(1L, reportedOutOfOrder.lastAttemptEpoch)

    @Test
    def writesTheTargetItNamesBackToItsOwnEpoch(): Unit = {
      val state = reportedOutOfOrder

      assertEquals(Some(1L), state.compiledAt.get(main))
      assertTrue(state.documentEnabledFor.isEmpty)
    }

    @Test
    def clearsTheFailedFiles(): Unit = {
      val failed = empty.failed(empty.startCompilation(a, b), Set(a, b))

      assertTrue(failed.succeeded(failed.startCompilation(c), Set(core), Set(c)).failedFiles.isEmpty)
    }

    @Test
    def keepsOnlyModificationsNewerThanItsOwnEpoch(): Unit = {
      val dispatched = empty.modified(Set(a))
      val token = dispatched.startCompilation(a)
      val duringTheBuild = dispatched.modified(Set(b))

      assertEquals(names(b), names(duringTheBuild.succeeded(token, Set(main), Set(a)).modifiedFiles))
    }

    @Test
    def dropsModificationsForFilesThatNoLongerExist(): Unit = {
      val dispatched = empty.modified(Set(a))
      val token = dispatched.startCompilation(a)
      val duringTheBuild = dispatched.modified(Set(b))
      delete(b)

      assertTrue(duringTheBuild.succeeded(token, Set(main), Set(a)).modifiedFiles.isEmpty)
    }

    @Test
    def replacesTheCoveredFiles(): Unit = {
      val first = empty.succeeded(empty.startCompilation(a), Set(main), Set(a, b))

      assertEquals(names(c), names(first.succeeded(first.startCompilation(c), Set(core), Set(c)).coveredFiles))
    }

    @Test
    def dropsCoveredFilesThatNoLongerExist(): Unit = {
      delete(b)

      assertEquals(names(a), names(empty.succeeded(empty.startCompilation(a), Set(main), Set(a, b)).coveredFiles))
    }

    /** An older compilation of `main` reporting after a newer one. */
    private def reportedOutOfOrder: CompilationState = {
      val first = empty.modified(Set(a))
      val older = first.startCompilation(a)
      val second = first.modified(Set(b))
      val newer = second.succeeded(second.startCompilation(b), Set(main), Set(b))

      newer.succeeded(older, Set(main), Set(a))
    }
  }

  @Nested
  class Failed {

    @Test
    def marksItsWholeScope(): Unit =
      assertEquals(names(a, b), names(empty.failed(empty.startCompilation(a, b), Set(a)).failedFiles))

    @Test
    def accumulatesAcrossAttempts(): Unit = {
      val first = empty.failed(empty.startCompilation(a), Set(a))

      assertEquals(names(a, c), names(first.failed(first.startCompilation(c), Set(c)).failedFiles))
    }

    @Test
    def assignsTheAttemptClockFromItsOwnEpoch(): Unit = {
      val state = empty.modified(Set(a))

      assertEquals(1L, state.failed(state.startCompilation(a), Set(a)).lastAttemptEpoch)
    }

    @Test
    def replacesTheCoveredFiles(): Unit = {
      val first = empty.failed(empty.startCompilation(a, b), Set(a, b))

      assertEquals(names(a), names(first.failed(first.startCompilation(a), Set(a)).coveredFiles))
    }

    @Test
    def dropsFilesThatNoLongerExist(): Unit = {
      val token = empty.startCompilation(a, b)
      delete(b)
      val state = empty.failed(token, Set(a, b))

      assertEquals(names(a), names(state.failedFiles))
      assertEquals(names(a), names(state.coveredFiles))
    }

    @Test
    def recordsNoBuild(): Unit = {
      val built = empty.succeeded(empty.startCompilation(a), Set(main), Set(a))
      val dispatched = built.modified(Set(a))
      val state = dispatched.failed(dispatched.startCompilation(a), Set(a))

      assertAll(
        () => assertEquals(built.compiledAt, state.compiledAt),
        () => assertEquals(built.lastBuildEpoch, state.lastBuildEpoch),
        () => assertEquals(1L, state.changesEpoch),
        () => assertEquals(names(a), names(state.modifiedFiles))
      )
    }
  }

  @Nested
  class Invalidated {

    @Test
    def forgetsEverythingButTheClocks(): Unit = {
      val edited = empty.modified(Set(a, b))
      val failed = edited.failed(edited.startCompilation(a, b), Set(a, b))
      val state = failed.succeeded(failed.startCompilation(c), Set(core), Set(c)).invalidated

      assertAll(
        () => assertTrue(state.compiledAt.isEmpty),
        () => assertTrue(state.modifiedAt.isEmpty),
        () => assertTrue(state.failedFiles.isEmpty),
        () => assertTrue(state.coveredFiles.isEmpty),
        () => assertEquals(2L, state.changesEpoch)
      )
    }
  }

  @Nested
  class DerivedValues {

    @Test
    def modifiedFilesAreTheKeysOfModifiedAt(): Unit = {
      val state = empty.modified(Set(a, b))

      assertEquals(state.modifiedAt.keySet, state.modifiedFiles)
    }

    @Test
    def documentEnabledForIsEmptyWhenNothingWasBuilt(): Unit =
      assertTrue(empty.documentEnabledFor.isEmpty)

    @Test
    def documentEnabledForHoldsEveryTargetOfTheLastBuild(): Unit = {
      val state = empty.succeeded(empty.startCompilation(a, c), Set(main, core), Set(a, c))

      assertEquals(Set(main, core), state.documentEnabledFor)
    }

    @Test
    def documentEnabledForDropsTargetsAnEarlierBuildLeftBehind(): Unit = {
      val first = empty.succeeded(empty.startCompilation(c), Set(core), Set(c))
      val dispatched = first.modified(Set(a))

      val second = dispatched.succeeded(dispatched.startCompilation(a), Set(main), Set(a))

      assertEquals(Set(main), second.documentEnabledFor)
    }
  }
}
