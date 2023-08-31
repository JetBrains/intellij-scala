package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.junit.Assert.assertEquals

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class ScalaHighlightUsagesHandlerTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with AssertionMatchers {

  def createHandler: HighlightUsagesHandlerBase[PsiElement]

  def assertHandlerIsNull(fileText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText)
    assert(createHandler == null)
  }

  def doTest(fileText: String, expected: Seq[String]): Unit = {
    myFixture.configureByText("dummy.scala", fileText)

    val handler = createHandler

    val targets = handler.getTargets
    handler.computeUsages(targets)

    val actualUsages: Seq[String] = handler.getReadUsages.asScala.map(_.substring(getFile.getText)).toSeq
    assertEquals(
      "Wrong usages",
      expected,
      actualUsages
    )
  }
}
