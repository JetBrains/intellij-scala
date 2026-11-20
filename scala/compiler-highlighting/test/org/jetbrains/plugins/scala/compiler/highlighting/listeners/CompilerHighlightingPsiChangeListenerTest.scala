package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.ServiceContainerUtil
import org.jetbrains.plugins.scala.compiler.highlighting.services.TriggerService
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.compiler.highlighting.util.SpyTriggerService
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.compiletime.uninitialized

/**
 * The listener is driven directly rather than through a real edit: the state write happens on the event, and
 * the compilation it goes on to schedule is debounced and irrelevant here.
 */
@RunWith(classOf[JUnit4])
class CompilerHighlightingPsiChangeListenerTest extends CompilationStateWritesTestBase:

  private var listener: CompilerHighlightingPsiChangeListener = uninitialized
  private var triggers: SpyTriggerService = uninitialized

  override protected def setUp(): Unit = 
    super.setUp()
    triggers = new SpyTriggerService(getProject)
    ServiceContainerUtil.replaceService(getProject, classOf[TriggerService], triggers, getTestRootDisposable)
    listener = new CompilerHighlightingPsiChangeListener(getProject)

  @Test
  def testAnEditInAScalaFileRecordsIt(): Unit = 
    val file = inSources("Edited.scala", "class Edited")
    assertRecordsModified(file)(childrenChanged(file))
  
  @Test
  def testAnEditInAJavaFileRecordsIt(): Unit = 
    val file = inSources("Edited.java", "class Edited {}")
    assertRecordsModified(file)(childrenChanged(file))
    
  @Test
  def testRepeatedEditsKeepTheFileAsTheOnlyModifiedOne(): Unit = 
    val file = inSources("Typed.scala", "class Typed")
    (1 to 5).foreach(_ => childrenChanged(file))
    assertEquals(Set(file), state.modifiedFiles)
    
  @Test
  def testAnEditInAnUntrackedFileRecordsNothing(): Unit = 
    val file = inSources("notes.txt", "text")
    assertRecordsNothing(childrenChanged(file))

  @Test
  def testAnEditInAResourceRecordsNothing(): Unit = 
    val file = inResources("reference.conf", "a = 1")
    assertRecordsNothing(childrenChanged(file))

  @Test
  def testAnEditOutsideEverySourceRootRecordsNothing(): Unit = 
    val file = outsideRoots("Detached.scala", "class Detached")
    assertRecordsNothing(childrenChanged(file))

  @Test
  def testAnEditInAnotherJvmSourceAdvancesTheClockOnly(): Unit = 
    val file = inSources("Edited.kt", "class Edited")
    assertRecordsOnlyClock(childrenChanged(file))

  @Test
  def testTheEventIsRecordedWithoutConsultingThePsiFileAgain(): Unit = 
    val file = inSources("Edited.scala", "class Edited")
    val psiFile = psiFileFor(file)
    childrenChanged(file)
    assertTrue("recorded against the VirtualFile", logic.isModified(psiFile.getVirtualFile))
  
  @Test
  def testAnEditInAClosedFileHighlightsTheSelectedFileAgain(): Unit =
    val closed = inSources("Foo.scala", "object Foo")
    val selected = inSources("Bar.scala", "object Bar")
    myFixture.openFileInEditor(selected)
    triggers.scheduled = List.empty
    childrenChanged(closed)
    assertTrue("the selected file is highlighted again", scheduledFiles.contains(selected))

  @Test
  def testTypingInTheSelectedFileSchedulesOnlyThatFile(): Unit =
    val selected = inSources("Bar.scala", "object Bar")
    myFixture.openFileInEditor(selected)
    triggers.scheduled = List.empty
    childrenChanged(selected)
    assertEquals(List(selected), scheduledFiles)

  @Test
  def testDeletingAMemberOfAClosedFileHighlightsTheSelectedFileAgain(): Unit =
    val closed = inSources("Foo.scala", "object Foo {\n  def foo(): Unit = {}\n}")
    val selected = inSources("Bar.scala", "object Bar {\n  Foo.foo()\n}")
    myFixture.openFileInEditor(selected)
    val foo = inReadAction(psiFileFor(closed).asInstanceOf[ScalaFile].typeDefinitions.head.functions.head)
    inWriteCommandAction(foo.delete())(using getProject)
    assertTrue("the selected file is highlighted again", scheduledFiles.contains(selected))

  @Test
  def testDeletingAClosedFileHighlightsTheSelectedFileAgain(): Unit =
    val closed = inSources("Foo.scala", "object Foo")
    val selected = inSources("Bar.scala", "object Bar {\n  Foo\n}")
    myFixture.openFileInEditor(selected)
    val psi = psiFileFor(closed)
    inWriteCommandAction(psi.delete())(using getProject)
    assertFalse("the file is gone", closed.isValid)
    assertTrue("the selected file is highlighted again", scheduledFiles.contains(selected))

  private def scheduledFiles: List[VirtualFile] = triggers.scheduled.map(_._1)

  private def childrenChanged(file: VirtualFile): Unit = 
    val event = new PsiTreeChangeEventImpl(PsiManager.getInstance(getProject))
    event.setFile(psiFileFor(file))
    listener.childrenChanged(event)
  

  private def psiFileFor(file: VirtualFile): PsiFile =
    inReadAction(PsiManager.getInstance(getProject).findFile(file))

