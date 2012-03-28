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

  def testSmartRenamed() {
    val fileText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BL<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BLLLL[Int](<caret>)
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "BLLLL").get, '\t')
    checkResultByText(resultText)
  }

  def testThis() {
    val fileText =
      """
      |class TT {
      |  val al: TT = <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  val al: TT = this<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "this").get, '\t')
    checkResultByText(resultText)
  }

  def testInnerThis() {
    val fileText =
      """
      |class TT {
      |  class GG {
      |    val al: GG = <caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  class GG {
      |    val al: GG = this<caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "this").get, '\t')
    checkResultByText(resultText)
  }

  def testOuterThis() {
    val fileText =
      """
      |class TT {
      |  class GG {
      |    val al: TT = <caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |class TT {
      |  class GG {
      |    val al: TT = TT.this<caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "TT.this").get, '\t')
    checkResultByText(resultText)
  }

  def testWhile() {
    val fileText =
      """
      |while (<caret>) {}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |while (true<caret>) {}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "true").get, '\t')
    checkResultByText(resultText)
  }

  def testDoWhile() {
    val fileText =
      """
      |do {} while (<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |do {} while (true<caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "true").get, '\t')
    checkResultByText(resultText)
  }

  def testNewFunction() {
    val fileText =
      """
      |val x: Int => String = new <caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |val x: Int => String = new Function1[Int, String] {
      |  def apply(v1: Int): String = <selection>null</selection>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "Function1").get, '\t')
    checkResultByText(resultText)
  }

  def testEtaExpansion() {
    val fileText =
      """
      |def foo(x: Int): String = x.toString
      |val x: Int => String = <caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    val resultText =
      """
      |def foo(x: Int): String = x.toString
      |val x: Int => String = foo _<caret>
      """.stripMargin.replaceAll("\r", "").trim()

    if (activeLookup != null) completeLookupItem(activeLookup.find(le => le.getLookupString == "foo").get, '\t')
    checkResultByText(resultText)
  }
}