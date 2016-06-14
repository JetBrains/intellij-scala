package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 14/06/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ScalaDocTest extends FailedResolveCaretTestBase {

  def testSCL10402(): Unit = {
    doResolveCaretTest(
      """
        |/** [[scala.collection.immutable.<caret>List$.apply object List's apply method]] */
      """.stripMargin)

  }
}
