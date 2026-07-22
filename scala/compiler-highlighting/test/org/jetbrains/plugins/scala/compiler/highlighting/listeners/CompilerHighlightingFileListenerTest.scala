package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.CompilerHighlightingTests
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.extensions.{inWriteAction, invokeAndWait}
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Drives real VFS operations and asserts what [[CompilerHighlightingFileListener]] recorded, which is the
 * half of the write path no editor is involved in: checkouts, external tools, deletions outside the IDE.
 */
@Category(Array(classOf[CompilerHighlightingTests]))
@RunWith(classOf[JUnit4])
class CompilerHighlightingFileListenerTest extends CompilationStateWritesTestBase {
  @Test
  def testCreatingACompiledSourceRecordsIt(): Unit = {
    val created = inSources("Created.scala")
    assertTrue("a created source has no output yet", logic.isModified(created))
  }
  @Test
  def testCreatingAJavaSourceRecordsIt(): Unit = {
    val created = inSources("Created.java")
    assertTrue(logic.isModified(created))
  }
  @Test
  def testCreatingAnUntrackedFileRecordsNothing(): Unit = {
    assertRecordsNothing(inSources("notes.txt"))
    assertRecordsNothing(inSources("script.py"))
    assertRecordsNothing(inSources("native.c"))
  }
  @Test
  def testCreatingAResourceRecordsNothing(): Unit =
    assertRecordsNothing(inResources("reference.conf"))
  @Test
  def testCreatingASourceOutsideEveryRootRecordsNothing(): Unit =
    assertRecordsNothing(outsideRoots("Detached.scala"))
  @Test
  def testCreatingAnotherJvmSourceAdvancesTheClockOnly(): Unit =
    assertRecordsOnlyClock(inSources("Created.kt"))
  @Test
  def testAnExternalContentChangeRecordsTheFile(): Unit = {
    val file = inSources("Edited.scala", "class Edited")
    assertRecordsModified(file)(writeExternally(file, "class Edited { def x = 1 }"))
  }
  @Test
  def testAnExternalContentChangeToAnUntrackedFileRecordsNothing(): Unit = {
    val file = inSources("notes.txt", "one")
    assertRecordsNothing(writeExternally(file, "two"))
  }
  @Test
  def testASaveOfTheDocumentRecordsNothing(): Unit = {
    val file = inSources("Saved.scala", "class Saved")
    val document = invokeAndWait(FileDocumentManager.getInstance().getDocument(file))
    invokeAndWait(inWriteAction {
      document.setText("class Saved { def x = 1 }")
      // Flush the PSI events the edit produces, so they cannot land inside the measured block below.
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
    })

    // Only the save is measured: the edit itself belongs to the PSI listener, whether or not it is attached.
    assertRecordsNothing(invokeAndWait(FileDocumentManager.getInstance().saveDocument(document)))
  }
  @Test
  def testDeletingACompiledSourceInvalidatesEverything(): Unit = {
    val file = inSources("Deleted.scala")
    markTargetBuilt(file)
    assertInvalidatesEverything(delete(file))
  }
  @Test
  def testDeletingAnUntrackedFileRecordsNothing(): Unit = {
    val file = inSources("notes.txt")
    assertRecordsNothing(delete(file))
  }
  @Test
  def testRenamingACompiledSourceInvalidatesEverything(): Unit = {
    val file = inSources("Before.scala")
    markTargetBuilt(file)
    assertInvalidatesEverything(rename(file, "After.scala"))
  }
  @Test
  def testMovingACompiledSourceInvalidatesEverything(): Unit = {
    val target = createSourceDir("destination")
    val file = inSources("Moved.scala")
    markTargetBuilt(file)
    assertInvalidatesEverything(move(file, target))
  }
  @Test
  def testAddingASourceDirectoryInvalidatesEverything(): Unit = {
    markTargetBuilt(inSources("Anchor.scala"))
    assertInvalidatesEverything(createSourceDir("added"))
  }
  @Test
  def testRemovingASourceDirectoryInvalidatesEverything(): Unit = {
    val directory = createSourceDir("doomed")
    markTargetBuilt(inSources("Anchor.scala"))
    assertInvalidatesEverything(delete(directory))
  }
  @Test
  def testADirectoryOutsideEverySourceRootRecordsNothing(): Unit = {
    markTargetBuilt(inSources("Anchor.scala"))
    assertRecordsNothing(outsideRoots("nested/Detached.scala"))
  }
}
