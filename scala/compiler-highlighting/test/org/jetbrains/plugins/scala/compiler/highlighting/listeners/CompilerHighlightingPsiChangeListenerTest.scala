package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.CompilerHighlightingTests
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import scala.compiletime.uninitialized

/**
 * The listener is driven directly rather than through a real edit: the state write happens on the event, and
 * the compilation it goes on to schedule is debounced and irrelevant here.
 */
@Category(Array(classOf[CompilerHighlightingTests]))
class CompilerHighlightingPsiChangeListenerTest extends CompilationStateWritesTestBase {

  private var listener: CompilerHighlightingPsiChangeListener = uninitialized

  override protected def setUp(): Unit = {
    super.setUp()
    listener = new CompilerHighlightingPsiChangeListener(getProject, getTestRootDisposable)
  }

  def testAnEditInAScalaFileRecordsIt(): Unit = {
    val file = inSources("Edited.scala", "class Edited")
    assertRecordsModified(file)(childrenChanged(file))
  }

  def testAnEditInAJavaFileRecordsIt(): Unit = {
    val file = inSources("Edited.java", "class Edited {}")
    assertRecordsModified(file)(childrenChanged(file))
  }

  def testRepeatedEditsKeepTheFileAsTheOnlyModifiedOne(): Unit = {
    val file = inSources("Typed.scala", "class Typed")
    (1 to 5).foreach(_ => childrenChanged(file))
    assertEquals(Set(file), state.modifiedFiles)
  }

  def testAnEditInAnUntrackedFileRecordsNothing(): Unit = {
    val file = inSources("notes.txt", "text")
    assertRecordsNothing(childrenChanged(file))
  }

  def testAnEditInAResourceRecordsNothing(): Unit = {
    val file = inResources("reference.conf", "a = 1")
    assertRecordsNothing(childrenChanged(file))
  }

  def testAnEditOutsideEverySourceRootRecordsNothing(): Unit = {
    val file = outsideRoots("Detached.scala", "class Detached")
    assertRecordsNothing(childrenChanged(file))
  }

  def testAnEditInAnotherJvmSourceAdvancesTheClockOnly(): Unit = {
    val file = inSources("Edited.kt", "class Edited")
    assertRecordsOnlyClock(childrenChanged(file))
  }

  def testTheEventIsRecordedWithoutConsultingThePsiFileAgain(): Unit = {
    val file = inSources("Edited.scala", "class Edited")
    val psiFile = psiFileFor(file)
    childrenChanged(file)
    assertTrue("recorded against the VirtualFile", logic.isModified(psiFile.getVirtualFile))
  }

  private def childrenChanged(file: VirtualFile): Unit = {
    val event = new PsiTreeChangeEventImpl(PsiManager.getInstance(getProject))
    event.setFile(psiFileFor(file))
    listener.childrenChanged(event)
  }

  private def psiFileFor(file: VirtualFile): PsiFile =
    inReadAction(PsiManager.getInstance(getProject).findFile(file))
}
