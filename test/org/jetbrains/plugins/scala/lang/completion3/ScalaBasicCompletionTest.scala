package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * User: Alefas
 * Date: 06.10.11
 */

class ScalaBasicCompletionTest extends ScalaCompletionTestBase {
  def testInImportSelector() {
    val fileText =
      """
      |import scala.collection.immutable.{VBuil<caret>}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |import scala.collection.immutable.{VectorBuilder<caret>}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "VectorBuilder").get)
    checkResultByText(resultText)
  }

  def testSCL3546() {
    val fileText =
      """
      |class C(private[this] val abcdef: Any)
      |new C(abcde<caret> = 0)
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class C(private[this] val abcdef: Any)
      |new C(abcdef<caret> = 0)
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "abcdef").get)
    checkResultByText(resultText)
  }

  def testRecursion() {
    val fileText =
      """
      |object Main {
      |  class A {
      |    val brrrrr = 1
      |  }
      |
      |  class Z {
      |    def d = 1
      |    def d_=(x: Int) {}
      |  }
      |
      |  class C(a: A) extends Z {
      |    override var d = a.br<caret>
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |object Main {
      |  class A {
      |    val brrrrr = 1
      |  }
      |
      |  class Z {
      |    def d = 1
      |    def d_=(x: Int) {}
      |  }
      |
      |  class C(a: A) extends Z {
      |    override var d = a.brrrrr<caret>
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "brrrrr").get)
    checkResultByText(resultText)
  }

  def testObjectCompletion() {
    val fileText =
      """
      |object States {
      |  class Nested
      |}
      |object C {
      |  val x: St<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |object States {
      |  class Nested
      |}
      |object C {
      |  val x: States.<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "States").get)
    checkResultByText(resultText)
  }

  def testObjectCompletionDotChar() {
    val fileText =
      """
      |object States {
      |  class Nested
      |}
      |object C {
      |  val x: St<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |object States {
      |  class Nested
      |}
      |object C {
      |  val x: States.<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "States").get, '.')
    checkResultByText(resultText)
  }

  def testPrivateMethod() {
    val fileText =
      """
      |class A {
      |  private def fooaa = 1
      |  def goo {
      |    foo<caret>
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  private def fooaa = 1
      |  def goo {
      |    fooaa<caret>
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "fooaa").get)
    checkResultByText(resultText)
  }

  def testParenthCompletionChar() {
    val fileText =
      """
      |val theMap = Map()
      |th<caret>
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |val theMap = Map()
      |theMap(<caret>)
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "theMap").get, '(')
    checkResultByText(resultText)
  }

  def testAfterNew() {
    val fileText =
      """
      |import collection.mutable.ListBuffer
      |class A {
      |  val f = new <caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |import collection.mutable.ListBuffer
      |class A {
      |  val f = new ListBuffer[<caret>]
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "ListBuffer").get, '[')
    checkResultByText(resultText)
  }

  def testAfterNewWithImport() {
    val fileText =
      """
      |class A {
      |  val f = new LBuff<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val resultText =
      """
      |import collection.mutable.ListBuffer
      |
      |class A {
      |  val f = new ListBuffer[<caret>]
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "ListBuffer").get, '[')
    checkResultByText(resultText)
  }

  def testSeq() {
    val fileText =
      """
      |class A {
      |  val f = Se<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  val f = Seq(<caret>)
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "Seq").get, '(')
    checkResultByText(resultText)
  }

  def testClosingParentheses() {
    val fileText =
      """
      |class A {
      |  def foo(x: AnR<caret>)
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  def foo(x: AnyRef)<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "AnyRef").get, ')')
    checkResultByText(resultText)
  }

  def testDeprecated() {
    val fileText =
      """
      |class A {
      |  @dep<caret>
      |  def foo {}
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  @deprecated<caret>
      |  def foo {}
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "deprecated").get, '\t')
    checkResultByText(resultText)
  }

  def testStringLength() {
    val fileText =
      """
      |class A {
      |  "".len<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  "".length<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "length").get, '\t')
    checkResultByText(resultText)
  }
}