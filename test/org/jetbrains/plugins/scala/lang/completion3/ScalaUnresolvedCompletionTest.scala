package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.completion.ScalaTextLookupItem
import org.jetbrains.plugins.scala.lang.completion3.ScalaCodeInsightTestBase.DEFAULT_CHAR
import org.junit.Assert.assertArrayEquals

/**
  * Created by Kate Ustiuzhanin on 24/03/2017.
  */
class ScalaUnresolvedCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testFieldVal(): Unit = {
    val fileText =
      s"""
         |class Test {
         |  def method(): Unit ={
         |    val doubleValue = 34.4
         |    if (doubleValue > intValue) {
         |      methodWithParams("Hey!")
         |    } else {
         |      methodWithoutParams()
         |    }
         |  }
         |
         |  val $CARET
         |}
      """.stripMargin
    doTest(fileText, "intValue")
  }


  def testFieldVar(): Unit = {
    val fileText =
      s"""
         |def foo(a: Int, b: Int): Unit = {
         |  val mid = a + (b - a) / 2
         |
         |  if (mid == value) {
         |    field1 = "got result"
         |    println(field1)
         |  } else if (mid > value) {
         |    foo1(a, mid - 1)
         |  } else {
         |    foo2(mid + 1, value)
         |  }
         |
         |  var $CARET
         |}
      """.stripMargin
    doTest(fileText, "field1", "value")
  }

  def testMethodWithUnresolvedParams(): Unit = {
    val fileText =
      s"""
         |def foo(a: Int, b: Int): Unit = {
         |  val mid = a + (b - a) / 2
         |
         |  if (mid == value) {
         |    field1 = "got result"
         |    println(field1)
         |  } else if (mid > value) {
         |    foo1(a, mid - 1)
         |  } else {
         |    foo2(mid + 1, value)
         |  }
         |
         |  def $CARET
         |}
      """.stripMargin
    doTest(fileText, "field1", "value", "foo1(i: Int, i1: Int)", "foo2(i: Int, value: Any)")
  }

  def testMethod(): Unit = {
    val fileText =
      s"""
         |class Test {
         |  def method(): Unit ={
         |    val doubleValue = 34.4
         |    if (doubleValue > intValue) {
         |      methodWithParams("Hey!")
         |    } else {
         |      methodWithoutParams()
         |    }
         |  }
         |
         |  def $CARET
         |}
      """.stripMargin
    doTest(fileText, "methodWithParams(str: String)", "methodWithoutParams()", "intValue")
  }

  def testMethodWithNamedParams(): Unit = {
    val fileText =
      s"""
         |class Test {
         |  def method(): Unit = {
         |    val doubleValue = 34.4
         |    if (doubleValue > intValue) {
         |      methodWithParams(a = "Hey!", b = 4, 23)
         |    } else {
         |      methoda(12, 23, " ", 23.3,  34, " ")
         |    }
         |  }
         |
         |  def $CARET
         |}
      """.stripMargin
    doTest(fileText, "methodWithParams(a: String, b: Int, i: Int)", "methoda(i: Int, i1: Int, str: String, d: Double, i2: Int, str1: String)", "intValue")
  }

  def testInfixMethodWithParams(): Unit = {
    val fileText =
      s"""
         |case class I(k: Int) {
         | I(1) add 3
         |
         | def $CARET
         |}
      """.stripMargin
    doTest(fileText, "add(i: Int)")
  }

  def testObject(): Unit = {
    val fileText =
      s"""
         |class Test {
         |  def method(): Unit ={
         |    val doubleValue = 34.4
         |    if (doubleValue > intValue) {
         |      methodWithoutParams("Hey!")
         |    } else {
         |      methodWithoutParams()
         |    }
         |  }
         |
         |  object $CARET
         |}
      """.stripMargin
    doTest(fileText, "intValue", "methodWithoutParams", "methodWithoutParams")
  }

  def testObjectSelected(): Unit = {
    val fileText =
      s"""
         |class Test {
         |  def method(): Unit = {
         |   methodWithoutParams("Hey!")
         |   object $CARET
         |  }
         |}
      """
    complete(fileText)

    getFixture.finishLookup(DEFAULT_CHAR)

    val expectedFileText =
      """
        |class Test {
        |  def method(): Unit = {
        |   methodWithoutParams("Hey!")
        |   object methodWithoutParams {
        |     def apply(str: String): Any = ???
        |   }
        |  }
        |}
      """.stripMargin
    checkResultByText(expectedFileText)
  }

  def testCaseClass(): Unit = {
    val fileText =
      s"""
         |class Test {
         |  def method(): Unit ={
         |    val doubleValue = 34.4
         |    if (doubleValue > intValue) {
         |      methodWithParams("Hey!")
         |    } else {
         |      methodWithoutParams()
         |    }
         |  }
         |
         |  case class $CARET
         |}
      """.stripMargin
    doTest(fileText, "methodWithParams(str: String)", "methodWithoutParams()")
  }

  def testClass(): Unit = {
    val fileText =
      s"""
         |object Test{
         | sealed trait $CARET
         |
         | case class ClassWithParams(firstParam: Long, secondParam: Option[String]) extends Base
         |
         | println(foo(3, 4))
         |
         | val typedVal: NewType = "Hey!"
         |}
      """.stripMargin
    doTest(fileText, "Base", "NewType")
  }

  def testTypeAlias(): Unit = {
    val fileText =
      s"""
         |object Test{
         | type $CARET
         |
         | case class ClassWithParams(firstParam: Long, secondParam: Option[String]) extends Base
         |
         | val typedVal: NewType = "Hey!"
         |
         | println(unresolved)
         |}
      """.stripMargin
    doTest(fileText, "Base", "NewType")
  }

  def testClassAfterNew(): Unit = {
    val fileText =
      s"""
         |class $CARET
         |new Test(12, "hi!")
      """.stripMargin

    doTest(fileText, "Test(i: Int, str: String)")
  }

  def testRanges(): Unit = {
    val fileText =
      s"""
         |class X {
         |  printX(12)
         |  printX("Sfd")
         |
         |  case class I(k: Int) {
         |    def addd(i: Int) = ???
         |
         |    3, 4) tail
         |
         |    def tt: Seq[Int] = ???
         |
         |  }
         |
         |  def foo(a: Int, b: Int): Unit = {
         |    val mid = a + (b - a) / 2
         |
         |    if (mid == value) {
         |      field1 = "got result"
         |      println(field1)
         |    } else if (mid > value) {
         |      foo1(a, mid - 1)
         |    } else {
         |      foo2(mid + 1, value)
         |    }
         |
         |    def $CARET
         |  }
         |}
      """.stripMargin
    doTest(fileText, "field1", "foo1(i: Int, i1: Int)", "foo2(i: Int, value: Any)", "value")
  }

  def testNoCompletionAfterOverrideField(): Unit =
    noCompletion(s"override val $CARET }")

  def testNoCompletionAfterOverrideClazz(): Unit =
    noCompletion(s"override class $CARET }")

  def testNoCompletionAfterOverrideType(): Unit =
    noCompletion(s"override type $CARET")

  def testNoCompletionAfterOverrideMethod(): Unit =
    noCompletion(s"override def $DEFAULT_CHAR }")

  private def noCompletion(text: String) = doTest(
    fileText =
      s"""
         |class Test {
         |  variable + 3
         |
         |  method(12, "test")
         |
         |  val t: TestType = _
         |$text
      """.stripMargin
  )

  private def complete(fileText: String): Array[LookupElement] = {
    val file = configureFromFileText(fileText).getVirtualFile

    val fixture = getFixture
    fixture.openFileInEditor(file)
    fixture.doHighlighting(ERROR)

    fixture.completeBasic()
  }

  private def doTest(fileText: String, expected: String*): Unit = {
    val actual = complete(fileText).collect {
      case item: ScalaTextLookupItem => item
    }.map(_.getLookupString)

    val actualSet = actual.sorted
    val expectedSet = expected.sorted
    assertArrayEquals(expectedSet.toArray[AnyRef], actualSet.toArray[AnyRef])
  }
}
