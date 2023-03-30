package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.project.ProjectUtil
import com.intellij.psi.PsiManager
import org.junit.Assert.{assertEquals, assertNotNull}

trait ProjectHighlightingAssertions {
  self: AllProjectHighlightingTest =>

  protected def assertFileShouldBeHighlighted(relativePath: String): Unit =
    assertFileShouldBeHighlighted(relativePath, shouldHighlight = true)

  protected def assertFileShouldNotBeHighlighted(relativePath: String): Unit =
    assertFileShouldBeHighlighted(relativePath, shouldHighlight = false)

  protected def assertFileShouldBeHighlighted(relativePath: String, shouldHighlight: Boolean): Unit = {
    val project = getProject
    val projectRoot = ProjectUtil.guessProjectDir(project);
    val file = projectRoot.findFileByRelativePath(relativePath)
    assertNotNull(s"Can't find file `$relativePath`", file)

    val psiFile = PsiManager.getInstance(project).findFile(file)
    val shouldHighlightActual = ProblemHighlightFilter.shouldHighlightFile(psiFile)
    val message = if (shouldHighlight)
      s"File must be highlighted: $relativePath"
    else
      s"File must not be highlighted: $relativePath"
    assertEquals(message, shouldHighlight, shouldHighlightActual)
  }
}
