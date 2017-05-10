package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert

/**
 * @author Alefas
 * @since 06.10.11
 */
class ScalaBasicCompletionTest extends ScalaCodeInsightTestBase {
  def testInImportSelector() {
    val fileText = "import scala.collection.immutable.{VBuil<caret>}"
    val resultText = "import scala.collection.immutable.{VectorBuilder<caret>}"

    doCompletionTest(fileText, resultText, "VectorBuilder")
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
      """

    val resultText =
      """
        |class A {
        |  A.xxxxx<caret>
        |}
        |object A {
        |  private val xxxxx = 1
        |}
      """

    doCompletionTest(fileText, resultText, "xxxxx")
  }

  def testVarCompletion() {
    val fileText =
      """
        |class A {
        |  A.<caret>
        |}
        |object A {
        |  var xxxxx = 1
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
        |class A {
        |  A.xxxxx<caret>
        |}
        |object A {
        |  var xxxxx = 1
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    assert(!lookups.exists(le => le.getLookupString == "xxxxx_="))

    finishLookup(lookups.find(le => le.getLookupString == "xxxxx").get)
    checkResultByText(resultText)
  }

  def testVarCompletion2() {
    val fileText =
      """
        |class A {
        |  A.<caret>
        |}
        |object A {
        |  var xxxxx = 1
        |}
      """

    val resultText =
      """
        |class A {
        |  A.xxxxx_=(<caret>)
        |}
        |object A {
        |  var xxxxx = 1
        |}
      """

    doCompletionTest(fileText, resultText, "xxxxx_=", time = 2)
  }


  def testNewInnerClass() {
    val fileText =
      """
      |class A {
      |  class BBBBB
      |  new BBBB<caret>
      |}
      """

    val resultText =
      """
      |class A {
      |  class BBBBB
      |  new BBBBB<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "BBBBB")
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
    val lookups = complete(1, CompletionType.BASIC)

    assert(lookups.collect {
      case le if le.getLookupString == "getGoo" => le
    }.length == 1)
  }

  def testSCL3546() {
    val fileText =
      """
      |class C(private[this] val abcdef: Any)
      |new C(abcde<caret> = 0)
      """

    val resultText =
      """
      |class C(private[this] val abcdef: Any)
      |new C(abcdef<caret> = 0)
      """

    doCompletionTest(fileText, resultText, "abcdef")
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
      """

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
      """

    doCompletionTest(fileText, resultText, "brrrrr")
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
      """

    val resultText =
      """
      |object States {
      |  class Nested
      |}
      |object C {
      |  val x: States<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "States")
  }

  def testImportObjectCompletion() {
    val fileText =
      """
        |object States {
        |  class Nested
        |}
        |object C {
        |  import St<caret>
        |}
      """

    val resultText =
      """
        |object States {
        |  class Nested
        |}
        |object C {
        |  import States<caret>
        |}
      """

    doCompletionTest(fileText, resultText, "States")
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
      """

    val resultText =
      """
      |object States {
      |  class Nested
      |}
      |object C {
      |  val x: States.<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "States", '.')
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
      """

    val resultText =
      """
      |class A {
      |  private def fooaa = 1
      |  def goo {
      |    fooaa<caret>
      |  }
      |}
      """

    doCompletionTest(fileText, resultText, "fooaa")
  }

  def testParenthCompletionChar() {
    val fileText =
      """
      |val theMap = Map()
      |th<caret>
      """

    val resultText =
      """
      |val theMap = Map()
      |theMap(<caret>)
      """

    doCompletionTest(fileText, resultText, "theMap", '(')
  }

  def testAfterNew() {
    val fileText =
      """
      |import collection.mutable.ListBuffer
      |class A {
      |  val f = new <caret>
      |}
      """

    val resultText =
      """
      |import collection.mutable.ListBuffer
      |class A {
      |  val f = new ListBuffer[<caret>]
      |}
      """

    doCompletionTest(fileText, resultText, "ListBuffer", '[')
  }

  def testAfterNewWithImport() {
    val fileText =
      """
      |class A {
      |  val f = new LBuff<caret>
      |}
      """

    val resultText =
      """
      |import scala.collection.mutable.ListBuffer
      |
      |class A {
      |  val f = new ListBuffer[<caret>]
      |}
      """

    doCompletionTest(fileText, resultText, "ListBuffer", '[')
  }

  def testSeq() {
    val fileText =
      """
      |class A {
      |  val f = Se<caret>
      |}
      """

    val resultText =
      """
      |class A {
      |  val f = Seq(<caret>)
      |}
      """

    doCompletionTest(fileText, resultText, "Seq", '(')
  }

  def testClosingParentheses() {
    val fileText =
      """
      |class A {
      |  def foo(x: AnR<caret>)
      |}
      """

    val resultText =
      """
      |class A {
      |  def foo(x: AnyRef)<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "AnyRef", ')')
  }

  def testDeprecated() {
    val fileText =
      """
      |class A {
      |  @dep<caret>
      |  def foo {}
      |}
      """

    val resultText =
      """
      |class A {
      |  @deprecated<caret>
      |  def foo {}
      |}
      """

    doCompletionTest(fileText, resultText, "deprecated")
  }

  def testStringLength() {
    val fileText =
      """
      |class A {
      |  "".len<caret>
      |}
      """

    val resultText =
      """
      |class A {
      |  "".length<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "length")
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
    val lookups = complete(0, CompletionType.BASIC)
    Assert.assertTrue(lookups.length == 2)
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
    val lookups = complete(0, CompletionType.BASIC)
    Assert.assertTrue(lookups.count(_.getLookupString == "foo") == 1)
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
    val lookups = complete(0, CompletionType.BASIC)
    Assert.assertTrue(lookups.count(_.getLookupString == "foo") == 2)
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
    val lookups = complete(0, CompletionType.BASIC)
    Assert.assertTrue(lookups.count(_.getLookupString == "foo") == 1)
  }

  def testHidingImplicits() {
    val fileText =
      """
      |"".<caret>
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(0, CompletionType.BASIC)
    Assert.assertTrue(!lookups.exists(_.getLookupString == "x"))
  }

  def testBasicRenamed() {
    val fileText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BL<caret>
      |}
      """

    val resultText =
      """
      |import java.util.{ArrayList => BLLLL}
      |object Test extends App {
      |  val al: java.util.List[Int] = new BLLLL[Int](<caret>)
      |}
      """

    doCompletionTest(fileText, resultText, "BLLLL")
  }

  def testYield() {
    val fileText =
      """
        |object Test extends App {
        |  Thread.<caret>
        |}
      """

    val resultText =
      """
      |object Test extends App {
      |  Thread.`yield`()
      |}
      """

    doCompletionTest(fileText, resultText, "`yield`")
  }

  def testInfix() {
    val fileText =
      """
      |class a {
      |  def foo(x: Int): Boolean = false
      |  false || this.fo<caret>
      |}
      """

    val resultText =
      """
      |class a {
      |  def foo(x: Int): Boolean = false
      |  false || this.foo(<caret>)
      |}
      """

    doCompletionTest(fileText, resultText, "foo")
  }

  def testPrefixedThis() {
    val fileText =
      """
      |class aaa {
      |  a<caret>
      |}
      """

    val resultText =
      """
      |class aaa {
      |  aaa.this<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "aaa.this")
  }

  def testPrefixedSuper() {
    val fileText =
      """
      |class aaa {
      |  a<caret>
      |}
      """

    val resultText =
      """
      |class aaa {
      |  aaa.super<caret>
      |}
      """

    doCompletionTest(fileText, resultText, "aaa.super")
  }

  def testCompanionObjectName() {
    val fileText =
      """
      |class aaa {
      |}
      |object a<caret>
      """

    val resultText =
      """
      |class aaa {
      |}
      |object aaa<caret>
      """

    doCompletionTest(fileText, resultText, "aaa")
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
    val lookups = complete(1, CompletionType.BASIC)

    assert(!lookups.exists(le => le.getLookupString == "getBar"))
  }

  def testBasicTypeCompletion() {
    val fileText =
      """
        |class Foo {
        |  val bar: Int<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    assert(lookups.count(le => le.getLookupString == "Int") == 2)
  }

  def testBasicTypeCompletionNoMethods() {
    val fileText =
      """
        |class Foo {
        |  def foo(): Int = 1
        |
        |  val bar: <caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    assert(!lookups.exists(le => le.getLookupString == "foo"))
  }

  def testBraceCompletionChar() {
    val fileText =
      """
        |class aaa {
        |  Seq(1, 2, 3).ma<caret>
        |}
      """

    val resultText =
      """
        |class aaa {
        |  Seq(1, 2, 3).map {<caret>}
        |}
      """

    doCompletionTest(fileText, resultText, "map", '{')
  }

  def testTailrecBasicCompletion() {
    val fileText =
      """
        |class aaa {
        |  @tail<caret>
        |  def goo() {}
        |}
      """

    val resultText =
      """
        |import scala.annotation.tailrec
        |
        |class aaa {
        |  @tailrec<caret>
        |  def goo() {}
        |}
      """

    doCompletionTest(fileText, resultText, "tailrec")
  }

  def testSCL4791() {
    val fileText =
      """
        |object PrivateInvisible {
        |  trait Requirement
        |
        |  trait Test {
        |    needs: Requirement =>
        |
        |    private def fault: Int = 7
        |    def work() {
        |      // Typing "fa", word "fault" is not present in the completion list
        |      val z = fa<caret>
        |    }
        |  }
        |}
      """

    val resultText =
      """
        |object PrivateInvisible {
        |  trait Requirement
        |
        |  trait Test {
        |    needs: Requirement =>
        |
        |    private def fault: Int = 7
        |    def work() {
        |      // Typing "fa", word "fault" is not present in the completion list
        |      val z = fault<caret>
        |    }
        |  }
        |}
      """

    doCompletionTest(fileText, resultText, "fault")
  }

  def testSCL4837() {
    val fileText =
      """
        |System.current<caret>TimeMillis()
      """

    val resultText =
      """
        |System.currentTimeMillis()<caret>
      """

    doCompletionTest(fileText, resultText, "currentTimeMillis")
  }

  def testParethsExists() {
    val fileText =
      """
        |def foo(x: Int) = 1
        |fo<caret>()
      """

    val resultText =
      """
        |def foo(x: Int) = 1
        |foo(<caret>)
      """

    doCompletionTest(fileText, resultText, "foo")
  }

  def testBracketsExists() {
    val fileText =
      """
        |clas<caret>[]
      """

    val resultText =
      """
        |classOf[<caret>]
      """

    doCompletionTest(fileText, resultText, "classOf")
  }

  def testBracketsExistsForType() {
    val fileText =
      """
        |val x: Opti<caret>[]
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
        |val x: Option[<caret>]
      """.stripMargin.replaceAll("\r", "").trim()

    finishLookup(lookups.find(le => le.getLookupString == "Option" &&
      le.getPsiElement.isInstanceOf[ScClass]).get, '[')

    checkResultByText(resultText)
  }

  def testBracketsWithoutParentheses() {
    val fileText =
      """
        |Array.app<caret>
      """

    val resultText =
      """
        |Array.apply[<caret>]
      """

    doCompletionTest(fileText, resultText, "apply", '[')
  }

  def testParenthesesCompletionChar() {
    val fileText = "System.c<caret>"
    val resultText = "System.currentTimeMillis(<caret>)"

    doCompletionTest(fileText, resultText, "currentTimeMillis", '(')
  }

  def testNoEtaExpansion() {
    val fileText = "List(1, 2, 3) takeRight<caret>"
    val resultText = "List(1, 2, 3) takeRight <caret>"

    doCompletionTest(fileText, resultText, "takeRight", ' ')
  }

  def testTypeIsFirst() {
    val fileText =
      """
        |class A {
        |  def typeSomething = 1
        |
        |  type<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    complete(1, CompletionType.BASIC)

    assert(getActiveLookup.get.getCurrentItem.getLookupString == "type", "Wrong item preselected.")
  }

  def testBackticks() {
    val fileText =
      """
        |object Z {
        |  def `foo` = 123
        |
        |  `f<caret>
        |}
      """

    val resultText =
      """
        |object Z {
        |  def `foo` = 123
        |
        |  `foo`<caret>
        |}
      """

    doCompletionTest(fileText, resultText, "`foo`")
  }

  def testStringSimple() {
    val fileText =
      """
        |object Z {
        |  val xxx = 1
        |  "$<caret>"
        |}
      """

    val resultText =
      """
        |object Z {
        |  val xxx = 1
        |  s"$xxx<caret>"
        |}
      """

    doCompletionTest(fileText, resultText, "xxx")
  }

  def testStringSimpleFunctionParameter() {
    val fileText =
      """
        |object Z {
        |  def xxx(yyy: Int) = "$<caret>"
        |}
      """

    val resultText =
      """
        |object Z {
        |  def xxx(yyy: Int) = s"$yyy<caret>"
        |}
      """

    doCompletionTest(fileText, resultText, "yyy")
  }

  def testStringNeedBraces() {
    val fileText =
      """
        |object Z {
        |  val xxx = 1
        |  "$<caret>asdfas"
        |}
      """

    val resultText =
      """
        |object Z {
        |  val xxx = 1
        |  s"${xxx<caret>}asdfas"
        |}
      """

    doCompletionTest(fileText, resultText, "xxx", '\n')
  }

  def testStringFunction() {
    val fileText =
      """
        |object Z {
        |  def xxx() = 1
        |  "$<caret>"
        |}
      """

    val resultText =
      """
        |object Z {
        |  def xxx() = 1
        |  s"${xxx()<caret>}"
        |}
      """

    doCompletionTest(fileText, resultText, "xxx")
  }

  def testInterpolatedStringDotCompletion() {
    val fileText =
      """
        |object Z {
        |  def xxx: String = "abc"
        |  s"$xxx.<caret>"
        |}
      """

    val resultText =
      """
        |object Z {
        |  def xxx: String = "abc"
        |  s"${xxx.substring(<caret>)}"
        |}
      """

    doCompletionTest(fileText, resultText, "substring")
  }

  def testObjectWithUnapplyStartWithLowCaseLetterAfterCaseLable(): Unit ={
    val fileText =
      """
        |trait A {
        |  object extractor {
        |    def unapply(x: Int) = Some(x)
        |  }
        |}
        |
        |class B extends A {
        |  1 match {
        |    case extr<caret> =>
        |  }
        |}
      """

    val resultText =
      """
        |trait A {
        |  object extractor {
        |    def unapply(x: Int) = Some(x)
        |  }
        |}
        |
        |class B extends A {
        |  1 match {
        |    case extractor =>
        |  }
        |}
      """

    doCompletionTest(fileText, resultText, "extractor")
  }

  def testCaseClassAfterCaseLable(): Unit ={
    val fileText =
      """
        |case class Extractor()
        |
        |class B extends A {
        |  1 match {
        |    case Extr<caret> =>
        |  }
        |}
      """

    val resultText =
      """
        |case class Extractor()
        |
        |class B extends A {
        |  1 match {
        |    case Extractor =>
        |  }
        |}
      """

    doCompletionTest(fileText, resultText, "Extractor")
  }

  def testCaseClassParamInValuePattern(): Unit = {
    val fileText =
      """case class Person(name: String)
        |val Person(na<caret>) = null"""
    val result =
      """case class Person(name: String)
        |val Person(name) = null"""

    doCompletionTest(fileText, result, "name")
  }

  def testCaseClassParamInCaseClause(): Unit = {
    val fileText =
      """case class Person(name: String)
        |Person("Johnny") match {
        |  case Person(na<caret>) =>
        |}"""
    val result =
      """case class Person(name: String)
        |Person("Johnny") match {
        |  case Person(name) =>
        |}"""

    doCompletionTest(fileText, result, "name")
  }

  def testCaseClassParamInGenerator(): Unit = {
    val fileText =
      """case class Person(name: String)
        |val guys: List[Person] = ???
        |for (Person(na<caret>) <- guys) {}"""
    val result =
      """case class Person(name: String)
        |val guys: List[Person] = ???
        |for (Person(name) <- guys) {}"""

    doCompletionTest(fileText, result, "name")
  }

  def testClassInPackageWithBackticks(): Unit ={
    val fileText =
      """
        |package `interface` {
        | class ScalaClass {
        |
        | }
        |}
        |
        |object Test {
        | new ScalaC<caret>
        |}
      """

    val result =
      """
        |import `interface`.ScalaClass
        |package `interface` {
        | class ScalaClass {
        |
        | }
        |}
        |
        |object Test {
        | new ScalaClass
        |}"""

    doCompletionTest(fileText, result, "ScalaClass")
  }
}