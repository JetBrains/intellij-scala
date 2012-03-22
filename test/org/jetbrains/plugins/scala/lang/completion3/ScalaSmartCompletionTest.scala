package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.10.11
 */

class ScalaSmartCompletionTest extends ScalaCompletionTestBase {
  def testAfterPlaceholder() {
    val fileText =
      """
      |class A {
      |  class B {def concat: B = new B}
      |  val f: B => B = _.<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  class B {def concat: B = new B}
      |  val f: B => B = _.concat<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "concat").get)
    checkResultByText(resultText)
  }

  def testAfterNew() {
    val fileText =
      """
      |import collection.mutable.ListBuffer
      |class A {
      |  val f: ListBuffer[String] = new <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import collection.mutable.ListBuffer
      |class A {
      |  val f: ListBuffer[String] = new ListBuffer[String]<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "ListBuffer").get, '[')
    checkResultByText(resultText)
  }
}