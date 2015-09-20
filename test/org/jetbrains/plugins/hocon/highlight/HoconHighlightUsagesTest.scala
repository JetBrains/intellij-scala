package org.jetbrains.plugins.hocon.highlight

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

/**
 * @author ghik
 */
class HoconHighlightUsagesTest extends LightPlatformCodeInsightFixtureTestCase {

  import org.junit.Assert._

  override def getTestDataPath: String = "testdata/hocon/highlight/usages"

  override def isWriteActionRequired: Boolean = false

  private def testUsages(expectedHighlights: (Int, Int, Int)*): Unit = {
    val nameNoPrefix = getTestName(false).stripPrefix("test")
    val testName = nameNoPrefix(0).toLower + nameNoPrefix.substring(1)
    val actualHighlights = myFixture.testHighlightUsages(testName + ".conf").toSeq.map { rh =>
      val logicalStart = myFixture.getEditor.offsetToLogicalPosition(rh.getStartOffset)
      val length = rh.getEndOffset - rh.getStartOffset
      (logicalStart.line, logicalStart.column, length)
    }
    assertEquals(expectedHighlights.toSet, actualHighlights.toSet)
  }

  def testSimple() = testUsages(
    (0, 0, 3),
    (1, 0, 3),
    (2, 0, 3),
    (3, 0, 3),
    (6, 0, 3),
    (7, 0, 3),
    (9, 12, 3)
  )

  def testNested() = testUsages(
    (4, 2, 3),
    (7, 4, 3),
    (10, 16, 3)
  )

  def testInArrayElement() = testUsages(
    (6, 3, 3),
    (6, 13, 3)
  )

  def testDifferentTexts() = testUsages(
    (0, 0, 3),
    (1, 0, 5),
    (2, 0, 5),
    (3, 0, 9),
    (4, 10, 7)
  )

  def testSingle() = testUsages()
}
