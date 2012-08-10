package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.junit.Assert

/**
 * @author Alefas
 * @since 06.10.11
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

  def testPrivateFromCompanionModule() {
    val fileText =
      """
        |class A {
        |  A.<caret>
        |}
        |object A {
        |  private val xxxxx = 1
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
        |class A {
        |  A.xxxxx<caret>
        |}
        |object A {
        |  private val xxxxx = 1
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "xxxxx").get)
    checkResultByText(resultText)
  }


  def testNewInnerClass() {
    val fileText =
      """
      |class A {
      |  class BBBBB
      |  new BBBB<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  class BBBBB
      |  new BBBBB<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "BBBBB").get)
    checkResultByText(resultText)
  }

  def testBeanProperty() {
    val fileText =
      """
        |import scala.reflect.BeanProperty
        |abstract class Foo {
        |  def setGoo(foo : String) {}
        |}
        |
        |class Bar() extends Foo {
        |  @BeanProperty var goo = "foo"
        |}
        |new Bar().<caret>
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    assert(activeLookup.collect {
      case le if le.getLookupString == "getGoo" => le
    }.length == 1)
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

  def testNamedParametersCompletion() {
    val fileText =
      """
      |class A {
      |  def foo(xxxx: Int) {
      |    foo(xxx<caret>)
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)
    Assert.assertTrue(activeLookup.length == 2)
  }
  
  def testHiding1() {
    val fileText =
      """
      |class SmartValueInitializerCompletion {
      |  def foo(x: Int) {}
      |  def foo(x: Boolean) {}
      |  def goo() {
      |    def foo(x: Int, y: Int) {}
      |    val x = 123
      |    f<caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)
    Assert.assertTrue(activeLookup.filter(_.getLookupString == "foo").length == 1)
  }

  def testHiding2() {
    val fileText =
      """
      |class SmartValueInitializerCompletion {
      |  def foo(x: Int) {}
      |  def foo(x: Boolean) {}
      |  f<caret>
      |  def goo() {
      |    def foo(x: Int, y: Int) {}
      |    val x = 123
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)
    Assert.assertTrue(activeLookup.filter(_.getLookupString == "foo").length == 2)
  }

  def testHiding3() {
    val fileText =
      """
      |class SmartValueInitializerCompletion {
      |  val foo: Int = 1
      |  def goo(foo: Int) {
      |    f<caret>
      |  }
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)
    Assert.assertTrue(activeLookup.filter(_.getLookupString == "foo").length == 1)
  }

  def testHidingImplicits() {
    val fileText =
      """
      |"".<caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(0, CompletionType.BASIC)
    Assert.assertTrue(activeLookup.filter(_.getLookupString == "x").length == 0)
  }

  def testBasicRenamed() {
    val fileText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BL<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

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

  def testYield() {
    val fileText =
      """
        |object Test extends App {
        |  Thread.<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |object Test extends App {
      |  Thread.`yield`()
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "`yield`").get, '\t')
    checkResultByText(resultText)
  }

  def testInfix() {
    val fileText =
      """
      |class a {
      |  def foo(x: Int): Boolean = false
      |  false || this.fo<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class a {
      |  def foo(x: Int): Boolean = false
      |  false || this.foo(<caret>)
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "foo").get, '\t')
    checkResultByText(resultText)
  }

  def testPrefixedThis() {
    val fileText =
      """
      |class aaa {
      |  a<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class aaa {
      |  aaa.this<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa.this").get, '\t')
    checkResultByText(resultText)
  }

  def testPrefixedSuper() {
    val fileText =
      """
      |class aaa {
      |  a<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class aaa {
      |  aaa.super<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa.super").get, '\t')
    checkResultByText(resultText)
  }

  def testCompanionObjectName() {
    val fileText =
      """
      |class aaa {
      |}
      |object a<caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class aaa {
      |}
      |object aaa<caret>
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "aaa").get, '\t')
    checkResultByText(resultText)
  }

  def testNoBeanCompletion() {
    val fileText =
      """
      |class Foo {
      |  val bar = 10
      |}
      |
      |new Foo().<caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    assert(activeLookup.find(le => le.getLookupString == "getBar") == None)
  }

  def testBasicTypeCompletion() {
    val fileText =
      """
        |class Foo {
        |  val bar: Int<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    assert(activeLookup.filter(le => le.getLookupString == "Int").length == 2)
  }

  def testBraceCompletionChar() {
    val fileText =
      """
        |class aaa {
        |  Seq(1, 2, 3).ma<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
        |class aaa {
        |  Seq(1, 2, 3).map {<caret>}
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "map").get, '{')
    checkResultByText(resultText)
  }
}