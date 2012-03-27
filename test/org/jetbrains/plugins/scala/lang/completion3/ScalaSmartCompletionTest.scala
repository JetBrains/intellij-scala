package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.junit.Assert

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
  
  def testFilterPrivates() {
    val fileText =
      """
      |class Test {
      |  def foo(): String = ""
      |  private def bar(): String = ""
      |}
      |
      |object O extends App {
      |  val s: String = new Test().bar<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertNull(activeLookup)
  }

  def testFilterObjectDouble() {
    val fileText =
      """
      |class Test {
      |  val x: Double = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertTrue(activeLookup.find(_.getLookupString == "Double") == None)
  }

  def testFalse() {
    val fileText =
      """
      |class A {
      |  val f: Boolean = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  val f: Boolean = false<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "false").get, '\t')
    checkResultByText(resultText)
  }

  def testClassOf() {
    val fileText =
      """
      |class A {
      |  val f: Class[_] = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class A {
      |  val f: Class[_] = classOf[<caret>]
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "classOf").get, '\t')
    checkResultByText(resultText)
  }
}