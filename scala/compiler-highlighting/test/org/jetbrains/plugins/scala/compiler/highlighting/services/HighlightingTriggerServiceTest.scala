package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.ServiceContainerUtil
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateWritesTestBase
import org.jetbrains.plugins.scala.compiler.highlighting.util.SpyHighlightingService
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.compiletime.uninitialized


@RunWith(classOf[JUnit4])
class HighlightingTriggerServiceTest extends CompilationStateWritesTestBase:

  private var spyCompilerService: SpyHighlightingService = uninitialized

  override def setUp(): Unit =
    super.setUp()

    spyCompilerService = new SpyHighlightingService(getProject)
    ServiceContainerUtil.replaceService(
      getProject,
      classOf[HighlightingService],
      spyCompilerService,
      getProject
    )

    assertSame(
      "Service wasn't replaced",
      spyCompilerService,
      HighlightingService(getProject)
    )
  
  private def triggerService: HighlightingTriggerService = HighlightingTriggerService(getProject)

  @Test
  def testDispatch_UpToDate_ServedFromStore(): Unit = runWithErrorsFromCompiler(getProject):
    val file = createFile("UpToDateFile.scala", "class UpToDateFile")
    CompilationLogicService(getProject).fileModified(file)
    markTargetBuilt(file)

    triggerService.processTrigger(file, "test up-to-date")

    assertTrue("Up-to-date file should NOT be dispatched to the compiler", spyCompilerService.dispatchedRequests.isEmpty)
  
  @Test
  def testDispatch_OutdatedFile_DispatchesRequest(): Unit = runWithErrorsFromCompiler(getProject):
    val file = createFile("Outdated.scala", "class Outdated")
    triggerService.processTrigger(file, "test outdated")

    // Assert that the service DID dispatch the request
    assertEquals("Outdated file SHOULD be dispatched", 1, spyCompilerService.dispatchedRequests.size)
    assertEquals(file, spyCompilerService.dispatchedRequests.head.file)

  private def createFile(name: String, content:String):VirtualFile =
    inReadAction:
      val file = inSources(name, content)
      val doc = FileDocumentManager.getInstance().getDocument(file)
      PsiDocumentManager.getInstance(getProject).commitAllDocuments()
      CompilationLogicService(getProject).fileModified(file)
      file