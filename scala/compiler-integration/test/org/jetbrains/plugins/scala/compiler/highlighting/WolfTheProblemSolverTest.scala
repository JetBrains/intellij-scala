package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{MockWolfTheProblemSolver, WolfTheProblemSolverImpl}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.Disposer
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Assert.{assertFalse, assertTrue}

import scala.concurrent.TimeoutException

class WolfTheProblemSolverTest extends ScalaCompilerHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def setUp(): Unit = {
    super.setUp()
    // By default, a mock instance of the Wolf is running in tests. We need to set it up manually.
    val mockWolf = WolfTheProblemSolver.getInstance(getProject).asInstanceOf[MockWolfTheProblemSolver]
    val theRealWolf = WolfTheProblemSolverImpl.createTestInstance(getProject).asInstanceOf[WolfTheProblemSolverImpl]
    mockWolf.setDelegate(theRealWolf)
    Disposer.register(getTestRootDisposable, theRealWolf)
  }

  def testRemoveSourceFile(): Unit = runWithErrorsFromCompiler(getProject) {
    // Create source files.
    val okFile = addFileToProjectSources("Ok.scala", "class Ok")
    val badFile = addFileToProjectSources("Bad.scala", "class Bad { unresolved }")

    // Trigger compilation.
    invokeAndWait {
      val descriptor = new OpenFileDescriptor(getProject, badFile)
      val editor = FileEditorManager.getInstance(getProject).openTextEditor(descriptor, true)
      // The tests are running in a headless environment where focus events are not propagated.
      // We need to call our listener manually.
      new CompilerHighlightingEditorFocusListener(editor).focusGained()
    }

    val wolf = WolfTheProblemSolver.getInstance(getProject)

    // Wait until the errors have been reported to the Wolf. Unfortunately, this is done asynchronously.
    retryUntilSuccess { wolf.isProblemFile(badFile) }

    assertTrue(wolf.isProblemFile(badFile))
    assertFalse(wolf.isProblemFile(okFile))

    // Remove the source file with compilation errors.
    VfsTestUtil.deleteFile(badFile)

    // Reporting errors to the Wolf (and clearing them) is done asynchronously.
    retryUntilSuccess { !wolf.isProblemFile(badFile) }
    assertFalse(wolf.isProblemFile(badFile))
    assertFalse(wolf.isProblemFile(okFile))
  }

  private def retryUntilSuccess(action: => Boolean): Unit = {
    var retries = 30
    while (retries > 0) {
      if (action) return
      Thread.sleep(1_000L)
      retries -= 1
    }

    throw new TimeoutException("Latest problem state has not been propagated to WolfTheProblemSolver")
  }
}
