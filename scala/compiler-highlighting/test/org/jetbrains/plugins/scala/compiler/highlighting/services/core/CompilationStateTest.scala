package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationState
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.junit.jupiter.api.Assertions.{assertAll, assertEquals, assertNotEquals, assertTrue}
import org.junit.jupiter.api.{Nested, Tag, Test}

@Tag(CompilerHighlightingCoreTest)
class CompilationStateTest extends CompilationStateTestBase:

  private val main = module("main")
  private val core = module("core")

  private val a = main / "A.scala"
  private val b = main / "B.scala"
  private val c = core / "C.scala"

  @Nested
  class Empty:

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
  
  @Nested
  class Modified:

    @Test
    def advancesTheChangeClockByOne(): Unit =
      assertEquals(1L, empty.edit(a).changesEpoch)

    @Test
    def stampsEveryFileWithTheNewEpoch(): Unit = 
      val state = empty.edit(a, b)

      assertEquals(Some(1L), state.modifiedAt.get(a))
      assertEquals(Some(1L), state.modifiedAt.get(b))
    
    @Test
    def keepsEarlierEntriesAtTheirOwnEpoch(): Unit = 
      val state = empty
        .edit(a)
        .edit(b)

      assertEquals(Some(1L), state.modifiedAt.get(a))
      assertEquals(Some(2L), state.modifiedAt.get(b))
    
    @Test
    def restampsAFileEditedAgain(): Unit =
      assertEquals(Some(2L), empty.edit(a)
        .edit(a)
        .modifiedAt.get(a)
      )

    @Test
    def withdrawsTheEditedFilesFromTheFailedFiles(): Unit =
      val failed = empty.compilationFailed(a, b)
      val edited = failed.edit(a)
      assertEquals(names(b), names(edited.failedFiles))
    
    @Test
    def ignoresFilesThatNoLongerExist(): Unit =
      delete(a)
      assertTrue(empty.edit(a).modifiedAt.isEmpty)
    
    @Test
    def prunesExistingEntriesForFilesThatNoLongerExist(): Unit =
      val state = empty.edit(a)
      delete(a)
      val edited = state.edit(b)
      assertEquals(names(b), names(edited.modifiedFiles))

    @Test
    def leavesTheBuildRecordAlone(): Unit = 
      val built = empty.compilationSucceeded(a)
      val state = built.modified(Set(a))

      assertAll(
        () => assertEquals(built.compiledAt, state.compiledAt),
        () => assertEquals(built.lastBuildEpoch, state.lastBuildEpoch),
        () => assertEquals(built.lastAttemptEpoch, state.lastAttemptEpoch),
        () => assertNotEquals(built.modifiedAt, state.modifiedAt)
      )
  
  @Nested
  class TokenFor:

    @Test
    def capturesTheCurrentEpochAndTheScope(): Unit = 
      val token = empty.edit(a).tokenFor(Set(a, b))

      assertEquals(1L, token.epoch)
      assertEquals(names(a, b), names(token.files))
    
    @Test
    def capturesAnEmptyScopeForABuildThatNamesNoFilesUpFront(): Unit = 
      val token = empty.edit(a).startCompilation()
      assertEquals(1L, token.epoch)
      assertTrue(token.files.isEmpty)
  
  @Nested
  class Succeeded:

    @Test
    def marksTheTargetsItBuilt(): Unit = 
      val state = empty.edit(a)
        .compilationSucceeded(a)

      assertEquals(Some(1L), state.compiledAt.get(main))
      assertEquals(None, state.compiledAt.get(core))
    
    @Test
    def keepsTargetsBuiltEarlier(): Unit = 
      val state = empty.compilationSucceeded(c)
        .edit(a)
        .compilationSucceeded(a)

      assertEquals(Some(0L), state.compiledAt.get(core))
      assertEquals(Some(1L), state.compiledAt.get(main))
    
    @Test
    def advancesTheBuildClockToItsOwnEpoch(): Unit = 
      val dispatched = empty.edit(a)

      assertEquals(1L, dispatched.compilationSucceeded(a).lastBuildEpoch)
    
    @Test
    def neverPullsTheBuildClockBackwards(): Unit =
      assertEquals(2L, reportedOutOfOrder.lastBuildEpoch)

    @Test
    def assignsTheAttemptClockFromItsOwnEpoch(): Unit =
      assertEquals(1L, reportedOutOfOrder.lastAttemptEpoch)

    @Test
    def writesTheTargetItNamesBackToItsOwnEpoch(): Unit = 
      val state = reportedOutOfOrder

      assertEquals(Some(1L), state.compiledAt.get(main))
      assertTrue(state.documentEnabledFor.isEmpty)
    
    @Test
    def clearsTheFailedFiles(): Unit = 
      val failed = empty.compilationFailed(a,b)

      assertTrue(failed.compilationSucceeded(c).failedFiles.isEmpty)
    
    @Test
    def keepsOnlyModificationsNewerThanItsOwnEpoch(): Unit = 
      val dispatched = empty.edit(a)
      val token = dispatched.startCompilation(a)
      val duringTheBuild = dispatched.edit(b)

      assertEquals(names(b), names(duringTheBuild.succeeded(token, Set(main), Set(a)).modifiedFiles))
    
    @Test
    def dropsModificationsForFilesThatNoLongerExist(): Unit = 
      val dispatched = empty.modified(Set(a))
      val token = dispatched.startCompilation(a)
      val duringTheBuild = dispatched.modified(Set(b))
      delete(b)

      assertTrue(duringTheBuild.succeeded(token, Set(main), Set(a)).modifiedFiles.isEmpty)
    
    @Test
    def replacesTheCoveredFiles(): Unit = 
      val first = empty.succeeded(empty.startCompilation(a), Set(main), Set(a, b))

      assertEquals(names(c), names(first.succeeded(first.startCompilation(c), Set(core), Set(c)).coveredFiles))
    
    @Test
    def dropsCoveredFilesThatNoLongerExist(): Unit = 
      delete(b)

      assertEquals(names(a), names(empty.succeeded(empty.startCompilation(a), Set(main), Set(a, b)).coveredFiles))
    
    /** An older compilation of `main` reporting after a newer one. In theory this should not happen. */
    private def reportedOutOfOrder: CompilationState = 
      val first = empty.edit(a)
      val older = first.startCompilation(a)
      val second = first.edit(b)
      val newer = second.compilationSucceeded(b)
      newer.succeeded(older, Set(main), Set(a))
  
  @Nested
  class Failed:

    @Test
    def marksItsWholeScope(): Unit =
      assertEquals(names(a, b), names(empty.compilationFailed(Set(a, b), Set(a)).failedFiles))

    @Test
    def accumulatesAcrossAttempts(): Unit = 
      val first = empty.compilationFailed(a)
      assertEquals(names(a, c), names(first.compilationFailed(c).failedFiles))
    
    @Test
    def assignsTheAttemptClockFromItsOwnEpoch(): Unit = 
      val state = empty.edit(a)

      assertEquals(1L, state.compilationFailed(a).lastAttemptEpoch)

    @Test
    def replacesTheCoveredFiles(): Unit = 
      val state = empty.compilationFailed(a, b)

      assertEquals(names(a), names(state.compilationFailed(a).coveredFiles))
    
    @Test
    def dropsFilesThatNoLongerExist(): Unit = 
      val token = empty.startCompilation(a, b)
      delete(b)
      val state = empty.failed(token, targetsOf(a,b), Set(a, b))

      assertEquals(names(a), names(state.failedFiles))
      assertEquals(names(a), names(state.coveredFiles))
    
    @Test
    def quarantinesItsTargetWhenNothingChangedSinceDispatch(): Unit =
      val state = empty.edit(a).compilationFailed(a)

      assertEquals(Set(main), state.failedTargets)

    @Test
    def quarantinesNothingAfterAnEditSinceDispatch(): Unit =
      val dispatched = empty.edit(a)
      val token = dispatched.startCompilation(a)
      val state = dispatched.edit(b).failed(token, Set(main), Set(a))

      assertEquals(Set.empty, state.failedTargets)

    @Test
    def doesNotMarkAFileEditedSinceDispatchAsFailed(): Unit =
      val dispatched = empty.edit(a, b)
      val token = dispatched.startCompilation(a, b)
      val state = dispatched.edit(b).failed(token, Set(main), Set(a, b))

      assertEquals(names(a), names(state.failedFiles))

    /**
     * `a` is broken; `b` is edited and dispatched alone; `a` is edited while that runs; it fails on `a`'s old
     * text. Had the failure quarantined `main`, `a` would answer `UpToDate (blocked)` and its edit would never be
     * compiled.
     */
    @Test
    def compilesAFileEditedWhileAnotherCompilationFailed(): Unit =
      val dispatched = empty.edit(a).compilationFailed(a).edit(b)
      val token = dispatched.startCompilation(b)

      dispatched.edit(a).failed(token, Set(main), Set(a, b))
        .on(a).assertIncremental(a)

    @Test
    def recordsNoBuild(): Unit =
      val built = empty.compilationSucceeded(a)
      val dispatched = built.edit(a)
      val state = dispatched.compilationFailed(a)

      assertAll(
        () => assertEquals(built.compiledAt, state.compiledAt),
        () => assertEquals(built.lastBuildEpoch, state.lastBuildEpoch),
        () => assertEquals(1L, state.changesEpoch),
        () => assertEquals(names(a), names(state.modifiedFiles))
      )

  @Nested
  class Invalidated:

    @Test
    def forgetsEverythingButTheClocks(): Unit = 
      val state = empty.edit(a, b)
        .compilationFailed(a,b)
        .compilationSucceeded(c)
        .invalidated

      assertAll(
        () => assertTrue(state.compiledAt.isEmpty),
        () => assertTrue(state.modifiedAt.isEmpty),
        () => assertTrue(state.failedFiles.isEmpty),
        () => assertTrue(state.coveredFiles.isEmpty),
        () => assertEquals(2L, state.changesEpoch)
      )
  
  @Nested
  class DerivedValues:

    @Test
    def modifiedFilesAreTheKeysOfModifiedAt(): Unit = 
      val state = empty.edit(a, b)

      assertEquals(state.modifiedAt.keySet, state.modifiedFiles)
      
    @Test
    def documentEnabledForIsEmptyWhenNothingWasBuilt(): Unit =
      assertTrue(empty.documentEnabledFor.isEmpty)

    @Test
    def documentEnabledForHoldsEveryTargetOfTheLastBuild(): Unit = 
      val state = empty.compilationSucceeded(a, c)

      assertEquals(Set(main, core), state.documentEnabledFor)
    
    @Test
    def documentEnabledForDropsTargetsAnEarlierBuildLeftBehind(): Unit = 
      val state = empty.compilationSucceeded(c)
        .edit(a)
        .compilationSucceeded(a)

      assertEquals(Set(main), state.documentEnabledFor)