package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.completion3.ScalaCodeInsightTestBase._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert.assertTrue

/**
  * @author Alefas
  * @since 06.10.11
  */
class ScalaBasicCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testInImportSelector(): Unit = doCompletionTest(
    fileText = s"import scala.collection.immutable.{VBuil$CARET}",
    resultText = s"import scala.collection.immutable.{VectorBuilder$CARET}",
    item = "VectorBuilder"
  )

  def testPrivateFromCompanionModule(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  private val xxxxx = 1
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  A.xxxxx$CARET
         |}
         |object A {
         |  private val xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx"
  )

  def testVarCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  A.xxxxx$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx"
  )

  def testVarNoCompletion(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx_="
  )

  def testVarCompletion2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  A.$CARET
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  A.xxxxx_=($CARET)
         |}
         |object A {
         |  var xxxxx = 1
         |}
      """.stripMargin,
    item = "xxxxx_=",
    time = 2
  )

  def testNewInnerClass(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  class BBBBB
         |  new BBBB$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  class BBBBB
         |  new BBBBB$CARET
         |}
      """.stripMargin,
    item = "BBBBB"
  )

  def testBeanProperty(): Unit = doMultipleCompletionTest(
    fileText =
      s"""
         |import scala.reflect.BeanProperty
         |abstract class Foo {
         |  def setGoo(foo : String) {}
         |}
         |
         |class Bar() extends Foo {
         |  @BeanProperty var goo = "foo"
         |}
         |new Bar().$CARET
      """.stripMargin,
    count = 1,
    item = "getGoo"
  )

  def testSCL3546(): Unit = doCompletionTest(
    fileText =
      s"""
         |class C(private[this] val abcdef: Any)
         |new C(abcde$CARET = 0)
      """.stripMargin,
    resultText =
      s"""
         |class C(private[this] val abcdef: Any)
         |new C(abcdef$CARET = 0)
      """.stripMargin,
    item = "abcdef"
  )

  def testRecursion(): Unit = doCompletionTest(
    fileText =
      s"""
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
         |    override var d = a.br$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
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
         |    override var d = a.brrrrr$CARET
         |  }
         |}
      """.stripMargin,
    item = "brrrrr"
  )

  def testObjectCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: St$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: States$CARET
         |}
      """.stripMargin,
    item = "States"
  )

  def testImportObjectCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  import St$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  import States$CARET
         |}
      """.stripMargin,
    item = "States"
  )

  def testObjectCompletionDotChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: St$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object States {
         |  class Nested
         |}
         |object C {
         |  val x: States.$CARET
         |}
      """.stripMargin,
    item = "States",
    char = '.'
  )

  def testPrivateMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  private def fooaa = 1
         |  def goo {
         |    foo$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private def fooaa = 1
         |  def goo {
         |    fooaa$CARET
         |  }
         |}
      """.stripMargin,
    item = "fooaa"
  )

  def testParenthCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |val theMap = Map()
         |th$CARET
      """.stripMargin,
    resultText =
      s"""
         |val theMap = Map()
         |theMap($CARET)
      """.stripMargin,
    item = "theMap",
    char = '('
  )

  def testAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""
         |import collection.mutable.ListBuffer
         |class A {
         |  val f = new $CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.mutable.ListBuffer
         |class A {
         |  val f = new ListBuffer[$CARET]
         |}
      """.stripMargin,
    item = "ListBuffer",
    char = '['
  )

  def testAfterNewWithImport(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f = new LBuff$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import scala.collection.mutable.ListBuffer
         |
         |class A {
         |  val f = new ListBuffer[$CARET]
         |}
      """.stripMargin,
    item = "ListBuffer",
    char = '['
  )

  def testSeq(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  val f = Se$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  val f = Seq($CARET)
         |}
      """.stripMargin,
    item = "Seq",
    char = '('
  )

  def testClosingParentheses(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  def foo(x: AnR$CARET)
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  def foo(x: AnyRef)$CARET
         |}
      """.stripMargin,
    item = "AnyRef",
    char = ')'
  )

  def testDeprecated(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  @dep$CARET
         |  def foo {}
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  @deprecated$CARET
         |  def foo {}
         |}
      """.stripMargin,
    item = "deprecated"
  )

  def testStringLength(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  "".len$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  "".length$CARET
         |}
      """.stripMargin,
    item = "length"
  )

  def testNamedParametersCompletion(): Unit = doMultipleCompletionTest(
    fileText =
      s"""
         |class A {
         |  def foo(xxxx: Int) {
         |    foo(xxx$CARET)
         |  }
         |}
      """.stripMargin,
    count = 2,
    time = 0,
    completionType = CompletionType.BASIC
  ) {
    _ => true
  }

  def testHiding1(): Unit = doMultipleCompletionTest(
    fileText =
      s"""
         |class SmartValueInitializerCompletion {
         |  def foo(x: Int) {}
         |  def foo(x: Boolean) {}
         |  def goo() {
         |    def foo(x: Int, y: Int) {}
         |    val x = 123
         |    f$CARET
         |  }
         |}
      """.stripMargin,
    count = 1,
    item = "foo",
    time = 0
  )

  def testHiding2(): Unit = doMultipleCompletionTest(
    fileText =
      s"""
         |class SmartValueInitializerCompletion {
         |  def foo(x: Int) {}
         |  def foo(x: Boolean) {}
         |  f$CARET
         |  def goo() {
         |    def foo(x: Int, y: Int) {}
         |    val x = 123
         |  }
         |}
      """.stripMargin,
    count = 2,
    item = "foo",
    time = 0
  )

  def testHiding3(): Unit = doMultipleCompletionTest(
    fileText =
      s"""
         |class SmartValueInitializerCompletion {
         |  val foo: Int = 1
         |  def goo(foo: Int) {
         |    f$CARET
         |  }
         |}
      """.stripMargin,
    count = 1,
    item = "foo",
    time = 0
  )

  def testBasicRenamed(): Unit = doCompletionTest(
    fileText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = new BL$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = new BLLLL[Int]($CARET)
         |}
      """.stripMargin,
    item = "BLLLL"
  )

  def testYield(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Test extends App {
         |  Thread.$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |object Test extends App {
        |  Thread.`yield`()
        |}
      """.stripMargin,
    item = "`yield`"
  )

  def testInfix(): Unit = doCompletionTest(
    fileText =
      s"""
         |class a {
         |  def foo(x: Int): Boolean = false
         |  false || this.fo$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class a {
         |  def foo(x: Int): Boolean = false
         |  false || this.foo($CARET)
         |}
      """.stripMargin,
    item = "foo"
  )

  def testNoPrefixedThis(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class aaa {
         |  a$CARET
         |}
      """.stripMargin,
    item = "aaa.this"
  )

  def testNoPrefixedSuper(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class aaa {
         |  a$CARET
         |}
      """.stripMargin,
    item = "aaa.super"
  )

  def testPrefixedThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  class bbb {
         |    a$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |  class bbb {
         |    aaa.this$CARET
         |  }
         |}
      """.stripMargin,
    item = "aaa.this"
  )

  def testPrefixedSuper(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait ttt
         |class aaa extends ttt {
         |  class bbb {
         |    a$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |trait ttt
         |class aaa extends ttt {
         |  class bbb {
         |    aaa.super$CARET
         |  }
         |}
      """.stripMargin,
    item = "aaa.super"
  )


  def testCompanionObjectName(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |}
         |object a$CARET
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |}
         |object aaa$CARET
      """.stripMargin,
    item = "aaa"
  )

  def testNoBeanCompletion(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Foo {
         |  val bar = 10
         |}
         |
         |new Foo().$CARET
      """.stripMargin,
    item = "getBar"
  )

  def testBasicTypeCompletion(): Unit = doMultipleCompletionTest(
    fileText =
      s"""
         |class Foo {
         |  val bar: Int$CARET
         |}
      """.stripMargin,
    count = 1,
    item = "Int"
  )

  def testCompanionObjectWithPackage(): Unit = doCompletionTest(
    fileText =
      s"""package foo
         |
         |class Foo {
         |  import F$CARET
         |}
         |
         |object Foo
       """.stripMargin,
    resultText =
      s"""package foo
         |
         |class Foo {
         |  import Foo$CARET
         |}
         |
         |object Foo
       """.stripMargin,
    item = "Foo"
  )

  def testObjectsCompletion(): Unit = doMultipleCompletionTest(
    s"""object Main {
       |  case class Foo()
       |
       |  trait Bar
       |  object Bar
       |  trait Bar2
       |
       |  class Baz
       |  object Baz
       |  class Baz2
       |
       |  object BarBaz
       |
       |  Main.$CARET
       |}
       """.stripMargin,
    CompletionType.BASIC,
    time = DEFAULT_TIME,
    count = 4
  ) {
    _.getLookupString match {
      case "Foo" | "Bar" | "Baz" | "BarBaz" => true
      case _ => false
    }
  }

  def testBasicTypeCompletionNoMethods(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Foo {
         |  def foo(): Int = 1
         |
         |  val bar: $CARET
         |}
      """.stripMargin,
    item = "foo"
  )

  def testBraceCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).ma$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class aaa {
         |  Seq(1, 2, 3).map {$CARET}
         |}
      """.stripMargin,
    item = "map",
    char = '{'
  )

  def testTailrecBasicCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class aaa {
         |  @tail$CARET
         |  def goo() {}
         |}
      """.stripMargin,
    resultText =
      s"""
         |import scala.annotation.tailrec
         |
         |class aaa {
         |  @tailrec$CARET
         |  def goo() {}
         |}
      """.stripMargin,
    item = "tailrec"
  )

  def testSCL4791(): Unit = doCompletionTest(
    fileText =
      s"""
         |object PrivateInvisible {
         |  trait Requirement
         |
         |  trait Test {
         |    needs: Requirement =>
         |
         |    private def fault: Int = 7
         |    def work() {
         |      // Typing "fa", word "fault" is not present in the completion list
         |      val z = fa$CARET
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |object PrivateInvisible {
         |  trait Requirement
         |
         |  trait Test {
         |    needs: Requirement =>
         |
         |    private def fault: Int = 7
         |    def work() {
         |      // Typing "fa", word "fault" is not present in the completion list
         |      val z = fault$CARET
         |    }
         |  }
         |}
      """.stripMargin,
    item = "fault"
  )

  def testSCL4837(): Unit = doCompletionTest(
    fileText = s"System.current$CARET()",
    resultText = s"System.currentTimeMillis()$CARET",
    item = "currentTimeMillis"
  )

  def testParenthesisExists(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Int) = 1
         |fo$CARET()
      """.stripMargin,
    resultText =
      s"""
         |def foo(x: Int) = 1
         |foo($CARET)
      """.stripMargin,
    item = "foo"
  )

  def testBracketsExists(): Unit = doCompletionTest(
    fileText = s"clas$CARET[]",
    resultText = s"classOf[$CARET]",
    item = "classOf"
  )

  def testBracketsExistsForType(): Unit = doCompletionTest(
    fileText = s"val x: Opti$CARET[]",
    resultText = s"val x: Option[$CARET]",
    char = '[',
    time = DEFAULT_TIME,
    completionType = CompletionType.BASIC
  ) { lookup =>
    hasLookupString(lookup, "Option") && lookup.getPsiElement.isInstanceOf[ScClass]
  }

  def testBracketsWithoutParentheses(): Unit = doCompletionTest(
    fileText = s"Array.app$CARET",
    resultText = s"Array.apply[$CARET]",
    item = "apply",
    char = '['
  )

  def testParenthesesCompletionChar(): Unit = doCompletionTest(
    fileText = s"System.c$CARET",
    resultText = s"System.currentTimeMillis($CARET)",
    item = "currentTimeMillis",
    char = '('
  )

  def testNoEtaExpansion(): Unit = doCompletionTest(
    fileText = s"List(1, 2, 3) takeRight$CARET",
    resultText = s"List(1, 2, 3) takeRight $CARET",
    item = "takeRight",
    char = ' '
  )

  def testTypeIsFirst(): Unit = {
    configureTest(fileText =
      s"""
         |class A {
         |  def typeSomething = 1
         |
         |  type$CARET
      """.stripMargin
    )

    assertTrue(activeLookup.exists { lookup =>
      hasLookupString(lookup.getCurrentItem, "type")
    })
  }

  def testBackticks(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def `foo` = 123
         |
         |  `f$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def `foo` = 123
         |
         |  `foo`$CARET
         |}
      """.stripMargin,
    item = "`foo`"
  )

  def testStringSimple(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  val xxx = 1
         |  "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  val xxx = 1
         |  s"$$xxx$CARET"
         |}
      """.stripMargin,
    item = "xxx"
  )

  def testStringSimpleFunctionParameter(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx(yyy: Int) = "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx(yyy: Int) = s"$$yyy$CARET"
         |}
      """.stripMargin,
    item = "yyy"
  )

  def testStringNeedBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  val xxx = 1
         |  "$$${CARET}asdfas"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  val xxx = 1
         |  s"$${xxx$CARET}asdfas"
         |}
      """.stripMargin,
    item = "xxx",
    char = '\n'
  )

  def testStringFunction(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx() = 1
         |  "$$$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx() = 1
         |  s"$${xxx()$CARET}"
         |}
      """.stripMargin,
    item = "xxx"
  )

  def testInterpolatedStringDotCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Z {
         |  def xxx: String = "abc"
         |  s"$$xxx.$CARET"
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Z {
         |  def xxx: String = "abc"
         |  s"$${xxx.substring($CARET)}"
         |}
      """.stripMargin,
    item = "substring"
  )

  def testCaseClassParamInValuePattern(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |val Person(na$CARET) = null
       """.stripMargin,
    resultText =
      """
        |case class Person(name: String)
        |val Person(name) = null
      """.stripMargin,
    item = "name"
  )

  def testCaseClassParamInCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |Person("Johnny") match {
         |  case Person(na$CARET) =>
         |}
      """.stripMargin,
    resultText =
      """
        |case class Person(name: String)
        |Person("Johnny") match {
        |  case Person(name) =>
        |}
      """.stripMargin,
    item = "name"
  )

  def testCaseClassParamInGenerator(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Person(name: String)
         |val guys: List[Person] = ???
         |for (Person(na$CARET) <- guys) {}
      """.stripMargin,
    resultText =
      """
        |case class Person(name: String)
        |val guys: List[Person] = ???
        |for (Person(name) <- guys) {}
      """.stripMargin,
    item = "name"
  )

  def testClassInPackageWithBackticks(): Unit = doCompletionTest(
    fileText =
      s"""
         |package `interface` {
         | class ScalaClass {
         |
         | }
         |}
         |
         |object Test {
         | new ScalaC$CARET
         |}
      """.stripMargin,
    resultText =
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
        |}
      """.stripMargin,
    item = "ScalaClass"
  )

  def testMirror(): Unit = doCompletionTest(
    fileText =
      s"""object Main {
         |
         |  class Foo {
         |    def bar(int: Int): Unit = {}
         |  }
         |
         |  val foo = new Foo
         |  foo.$CARET
         |  foo.bar(42)
         |}
       """.stripMargin,
    resultText =
      s"""object Main {
         |
         |  class Foo {
         |    def bar(int: Int): Unit = {}
         |  }
         |
         |  val foo = new Foo
         |  foo.bar($CARET)
         |  foo.bar(42)
         |}
       """.stripMargin,
    item = "bar"
  )

  def testPackageObject(): Unit = doCompletionTest(
    fileText =
      s"""package object foo {
         |  class Foo
         |}
         |
         |import foo.$CARET
       """.stripMargin,
    s"""package object foo {
       |  class Foo
       |}
       |
       |import foo.Foo$CARET
     """.stripMargin,
    item = "Foo"
  )

  def testPredefinedConversion(): Unit = doCompletionTest(
    fileText = s""""1".he$CARET""",
    resultText = s""""1".headOption$CARET""",
    item = "headOption"
  )

  def testPredefinedConversionsCollision(): Unit = doCompletionTest(
    fileText = s"1.toBin$CARET",
    resultText = s"1.toBinaryString$CARET",
    item = "toBinaryString"
  )

}
