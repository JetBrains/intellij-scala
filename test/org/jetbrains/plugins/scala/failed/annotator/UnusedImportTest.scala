package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.imports.unused.UnusedImportTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class UnusedImportTest extends UnusedImportTestBase {
  def testSCL9538(): Unit = {
    val text =
      """
        |import scala.concurrent.ExecutionContext
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |class AppModel(implicit ec: ExecutionContext) {
        |
        |}
        |
        |val x = new AppModel
      """.stripMargin
    assert(messages(text).isEmpty)
  }
}
