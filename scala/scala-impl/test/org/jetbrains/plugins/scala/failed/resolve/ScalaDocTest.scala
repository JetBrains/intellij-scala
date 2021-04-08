package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by Anton Yalyshev on 14/06/16.
  */
class ScalaDocTest extends FailedResolveCaretTestBase {

  def testSCL10402(): Unit = {
    doResolveCaretTest(
      """
        |/** [[scala.collection.immutable.<caret>List$.apply object List's apply method]] */
      """.stripMargin)

  }
}
