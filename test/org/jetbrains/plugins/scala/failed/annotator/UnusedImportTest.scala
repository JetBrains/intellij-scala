package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

import scala.collection.JavaConverters._

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class UnusedImportTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def doTest(text: String): Unit = {
    myFixture.configureByText("dummy.scala", text)
    val warnings = myFixture.doHighlighting().asScala.map(_.getDescription)
    assert(!warnings.contains("Unused import statement"), "Unused import found")
  }

  def testSCL9538(): Unit = {
    val text =
      """import scala.concurrent.ExecutionContext
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |class AppModel(implicit ec: ExecutionContext) {
        |
        |}
        |
        |val x = new AppModel
      """.stripMargin
    doTest(text)
  }
}
