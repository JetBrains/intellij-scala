package org.jetbrains.plugins.scala.highlighter.usages

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.MarkersUtils

abstract class ScalaHighlightUsagesTestBase
  extends ScalaLightCodeInsightFixtureTestCase {

  protected val start = MarkersUtils.startMarker
  protected val end = MarkersUtils.endMarker

  protected var scalaHighlightUsagesTestFixture: ScalaHighlightUsagesTestFixture = _

  override protected def setUp(): Unit = {
    super.setUp()

    scalaHighlightUsagesTestFixture = new ScalaHighlightUsagesTestFixture(myFixture, startMarker = start, endMarker = end)
  }

  protected def doTest(fileText: String): Unit = {
    scalaHighlightUsagesTestFixture.doTest(fileText)
  }

  protected def multiCaret(i: Int): String = scalaHighlightUsagesTestFixture.multiCaret(i)

  protected def doTestWithDifferentCarets(fileText: String): Unit = {
    scalaHighlightUsagesTestFixture.doTestWithDifferentCarets(fileText)
  }
}
