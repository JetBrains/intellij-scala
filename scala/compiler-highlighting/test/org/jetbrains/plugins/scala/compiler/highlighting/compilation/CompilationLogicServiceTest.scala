package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationStateTestBase.CompilerHighlightingTests
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilationLogicService
import org.junit.jupiter.api.Assertions.{assertAll, assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.compiletime.uninitialized

/**
 * What [[CompilationLogicService]] adds over the value it holds: which transition each method applies, how
 * often, and that concurrent writers all land.
 *
 * The transitions themselves belong to [[CompilationStateTest]] and are not repeated here. `decide` is the
 * only method that reads the project — to resolve a build target — so a null one covers everything else,
 * and the rule table it delegates to is covered against [[CompilationStateTestBase.targetOf]] instead.
 */
@Tag(CompilerHighlightingTests)
class CompilationLogicServiceTest extends CompilationStateTestBase {

  private val main = module("main")
  private val a = file("A.scala", in = main)
  private val b = file("B.scala", in = main)

  private var logic: CompilationLogicService = uninitialized

  @BeforeEach
  def freshService(): Unit = logic = new CompilationLogicService(null)

  /**
   * The guard in `filesModified` is load-bearing: `modified` advances the clock before it looks at the
   * files, so recording an empty set would make every target stale and buy a project-wide recompilation for
   * a change that did not happen. The VFS listener reaches this — its `collect` yields nothing when the
   * surviving changes are creations the VFS could not resolve to a file.
   */
  @Test
  def modifyingNoFilesChangesNothing(): Unit = {
    markBuilt()
    val before = logic.snapshot
    logic.filesModified(Set.empty)
    assertEquals(before, logic.snapshot)
  }

  @Test
  def modifyingFilesAdvancesTheClockOnce(): Unit = {
    val before = logic.snapshot.changesEpoch
    logic.filesModified(Set(a, b))
    assertAll(
      () => assertEquals(before + 1, logic.snapshot.changesEpoch),
      () => assertEquals(names(a, b), names(logic.snapshot.modifiedFiles))
    )
  }

  @Test
  def modifyingOneFileRecordsThatFileAlone(): Unit = {
    logic.fileModified(a)
    assertAll(
      () => assertEquals(1L, logic.snapshot.changesEpoch),
      () => assertEquals(names(a), names(logic.snapshot.modifiedFiles))
    )
  }

  /**
   * `compilationStarted` only reads. Were it ever to write, a compilation would commit against the state as
   * it stands when it ends rather than the one it ran against, and every edit made while it ran would be
   * absorbed by its success.
   */
  @Test
  def startingACompilationOnlyReads(): Unit = {
    logic.fileModified(a)
    val before = logic.snapshot
    val token = logic.compilationStarted(Set(a))
    assertAll(
      () => assertEquals(before, logic.snapshot),
      () => assertEquals(before.changesEpoch, token.epoch),
      () => assertEquals(names(a), names(token.files))
    )
  }

  @Test
  def aSucceededCompilationMakesItsTargetCurrent(): Unit = {
    markBuilt()
    assertTrue(isCurrent, "the built target must read as current")
  }

  @Test
  def aBuildInputChangeLeavesNoTargetCurrent(): Unit = {
    markBuilt()
    logic.buildInputChanged()
    assertFalse(isCurrent)
  }

  @Test
  def invalidatingLeavesNoTargetCurrent(): Unit = {
    markBuilt()
    logic.invalidateAll()
    assertFalse(isCurrent)
  }

  @Test
  def concurrentModificationsAreAllRecorded(): Unit = {
    val files = (1 to 64).map(i => file(s"F$i.scala", in = main))
    val pool = Executors.newFixedThreadPool(8)
    val start = new CountDownLatch(1)
    val finished = new CountDownLatch(files.size)
    try {
      files.foreach(f => pool.execute { () =>
        start.await()
        logic.fileModified(f)
        finished.countDown()
      })
      start.countDown()
      assertTrue(finished.await(30, TimeUnit.SECONDS), "every writer must finish")
    } finally pool.shutdownNow()

    assertAll(
      () => assertEquals(files.size.toLong, logic.snapshot.changesEpoch, "one advance per write, none lost"),
      () => assertEquals(names(files *), names(logic.snapshot.modifiedFiles))
    )
  }

  /** Puts `main` in `compiledAt` at the current epoch, so that losing its currency is observable. */
  private def markBuilt(): Unit = {
    val token = logic.compilationStarted(Set(a))
    logic.compilationSucceeded(token, Set(main), Set(a))
  }

  private def isCurrent: Boolean = {
    val current = logic.snapshot
    current.compiledAt.get(main).contains(current.changesEpoch)
  }
}
