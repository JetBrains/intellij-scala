package org.jetbrains.plugins.scala.worksheet

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.assertTrue

class WorksheetFileInTestSourcesTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def placeSourceFilesInTestContentRoot: Boolean = true

  def testScFileInTestSourcesIsWorksheet(): Unit = {
    val file = myFixture.configureByText("worksheet.sc", "val answer = 42")

    assertTrue(WorksheetUtils.isWorksheetFile(getProject, file.getVirtualFile))
  }
}
