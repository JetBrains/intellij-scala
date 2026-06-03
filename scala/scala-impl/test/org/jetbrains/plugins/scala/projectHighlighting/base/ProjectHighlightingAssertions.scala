package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertFalse, assertTrue}

trait ProjectHighlightingAssertions {
  self: AllProjectHighlightingTest =>

  protected def assertFileShouldBeHighlighted(relativePath: String): Unit =
    assertFileShouldBeHighlighted(relativePath, shouldHighlight = true)

  protected def assertFileShouldNotBeHighlighted(relativePath: String): Unit =
    assertFileShouldBeHighlighted(relativePath, shouldHighlight = false)

  protected def assertFileShouldBeHighlighted(relativePath: String, shouldHighlight: Boolean): Unit = {
    val psiFile = TestUtils.findFileInProject(getProject, relativePath)
    val shouldHighlightActual = ProblemHighlightFilter.shouldHighlightFile(psiFile)

    val file = psiFile.getVirtualFile
    if (shouldHighlight) {
      assertTrue(s"File should be highlighted: $relativePath (${file.getCanonicalPath})", shouldHighlightActual)
    } else {
      assertFalse(s"File should not be highlighted: $relativePath (${file.getCanonicalPath})", shouldHighlightActual)
    }
  }
}
