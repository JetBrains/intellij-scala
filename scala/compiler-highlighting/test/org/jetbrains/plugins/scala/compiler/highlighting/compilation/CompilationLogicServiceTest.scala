package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.CompilerHighlightingTests
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

@Category(Array(classOf[CompilerHighlightingTests]))
@RunWith(classOf[JUnit4])
class CompilationLogicServiceTest extends CompilationStateWritesTestBase {

  /**
   * The guard in `filesModified` is load-bearing: `modified` advances the clock before it looks at the
   * files, so recording an empty set would make every target stale and buy a project-wide recompilation for
   * a change that did not happen. The VFS listener reaches this — its `collect` yields nothing when the
   * surviving changes are creations the VFS could not resolve to a file.
   */
  @Test
  def testModifyingNoFilesChangesNothing(): Unit = 
    markTargetBuilt(inSources("A.scala"))
    assertRecordsNothing(logic.filesModified(Set.empty))
  

  @Test
  def testModifyingFilesWhileCompilerHighlightingIsOffChangesNothing(): Unit = 
    val a = inSources("A.scala")
    val b = inSources("B.scala")
    markTargetBuilt(a)
    ScalaHighlightingMode.compilerHighlightingEnabledInTests = false
    assertRecordsNothing(logic.filesModified(Set(a, b)))
  

  @Test
  def testModifyingFilesAdvancesTheClockOnce(): Unit = 
    val a = inSources("A.scala")
    val b = inSources("B.scala")
    val before = state.changesEpoch
    assertRecordsModified(a, b)(logic.filesModified(Set(a, b)))
    assertEquals(before + 1, state.changesEpoch)
  

  @Test
  def testModifyingOneFileRecordsThatFileAlone(): Unit = 
    val a = inSources("A.scala")
    val before = state.changesEpoch
    assertRecordsModified(a)(logic.fileModified(a))
    assertEquals(before + 1, state.changesEpoch)
  

  /**
   * `compilationStarted` only reads. Were it ever to write, a compilation would commit against the state as
   * it stands when it ends rather than the one it ran against, and every edit made while it ran would be
   * absorbed by its success.
   */
  @Test
  def testStartingACompilationOnlyReads(): Unit = 
    val a = inSources("A.scala")
    val before = state
    val token = logic.compilationStarted(Set(a))
    assertEquals("nothing should have been recorded", before, state)
    assertEquals(before.changesEpoch, token.epoch)
    assertEquals(Set(a), token.files)
  

  @Test
  def testASucceededCompilationMakesItsTargetCurrent(): Unit = 
    val a = inSources("A.scala")
    markTargetBuilt(a)
    assertTrue("the built target must read as current", isCurrent(a))
  

  @Test
  def testABuildInputChangeLeavesNoTargetCurrent(): Unit = 
    val a = inSources("A.scala")
    markTargetBuilt(a)
    assertInvalidatesEverything(logic.buildInputChanged())
    assertFalse(isCurrent(a))
  

  @Test
  def testInvalidatingLeavesNoTargetCurrent(): Unit = 
    val a = inSources("A.scala")
    markTargetBuilt(a)
    assertInvalidatesEverything(logic.invalidateAll())
    assertFalse(isCurrent(a))
  

  @Test
  def testConcurrentModificationsAreAllRecorded(): Unit = 
    val files = (1 to 64).map(i => inSources(s"F$i.scala"))
    val before = state.changesEpoch
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
      assertTrue("every writer must finish", finished.await(30, TimeUnit.SECONDS))
    } finally pool.shutdownNow()

    assertEquals("one advance per write, none lost", before + files.size, state.changesEpoch)
    assertTrue("every file must be recorded", files.forall(state.modifiedFiles.contains))
  

  private def isCurrent(file: VirtualFile): Boolean =
    ModuleKey.of(getProject, file).exists(target => state.compiledAt.get(target).contains(state.changesEpoch))
}
