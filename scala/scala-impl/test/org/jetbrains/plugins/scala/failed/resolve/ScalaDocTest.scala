package org.jetbrains.plugins.scala.failed.resolve

class ScalaDocTest extends FailedResolveCaretTestBase {

  def testSCL10402(): Unit = {
    doResolveCaretTest(
      """
        |/** [[scala.collection.immutable.<caret>List$.apply object List's apply method]] */
      """.stripMargin)

  }
}
