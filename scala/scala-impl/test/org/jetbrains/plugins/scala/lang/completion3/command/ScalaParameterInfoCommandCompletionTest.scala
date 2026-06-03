package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.scala.util.EditorHintFixtureEx
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.Test

//noinspection ApiStatus,UnstableApiUsage
final class ScalaParameterInfoCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private var editorHintFixture: EditorHintFixtureEx = _
  private val ParamInfoPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Parameter info")

  protected override def setUp(): Unit = {
    super.setUp()
    editorHintFixture = new EditorHintFixtureEx(getTestRootDisposable)
  }

  private def doParamInfoCommandCompletionTest(fileText: String, expectedHintText: String): Unit = {
    doCommandCompletionTest(fileText, predicate = ParamInfoPredicate)
    waitForParamInfoHint()
    val actualHintText = editorHintFixture.getCurrentHintText(stripHtml = true)
    assertNotNull("Parameter info hint is not shown", actualHintText)
    assertEquals(expectedHintText, actualHintText)
  }

  private def checkNoParamInfoCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = ParamInfoPredicate)

  /** Based on [[com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.getParameterInfoAtCaret]] */
  private def waitForParamInfoHint(): Unit = for (_ <- 0 until 10) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
  }

  @Test
  def functionParams(): Unit = {
    doParamInfoCommandCompletionTest(
      fileText =
        s"""object Test {
           |  def foo(i: Int, s: String, b: Boolean) = {}
           |  foo(..$CARET)
           |}""".stripMargin,
      expectedHintText = "<b>i: Int</b>, s: String, b: Boolean"
    )
  }

  @Test
  def functionNoParams(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo() = x
         |  foo(..$CARET)
         |}""".stripMargin,
    expectedHintText = "&lt;no parameters&gt;" // encoded <no parameters>
  )

  @Test
  def functionSingleParam(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(x: Int) = x
         |  foo(..$CARET)
         |}""".stripMargin,
    expectedHintText = "<b>x: Int</b>"
  )

  @Test
  def functionSecondParam(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(x: Int, y: String) = {}
         |  foo(1, ..$CARET)
         |}""".stripMargin,
    expectedHintText = "x: Int, <b>y: String</b>"
  )

  @Test
  def functionDefaultParam(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(x: Int = 42) = x
         |  foo(..$CARET)
         |}""".stripMargin,
    expectedHintText = "<b>x: Int = …</b>"
  )

  @Test
  def curriedFunctionFirstList(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(a: Int)(b: String) = ???
         |  foo(..$CARET)
         |}""".stripMargin,
    expectedHintText = "(<b>a: Int</b>)(b: String)"
  )

  @Test
  def curriedFunctionSecondList(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(a: Int)(b: String) = ???
         |  foo(1)(..$CARET)
         |}""".stripMargin,
    expectedHintText = "(a: Int)(<b>b: String</b>)"
  )

  @Test
  def constructorParams(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Foo(n: Int, s: String)
         |
         |object Test {
         |  new Foo(..$CARET)
         |}""".stripMargin,
    expectedHintText = "<b>n: Int</b>, s: String"
  )

  @Test
  def caseClassApply(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""case class Bar(id: Int, name: String)
         |
         |object Test {
         |  Bar(..$CARET)
         |}""".stripMargin,
    expectedHintText = "<b>id: Int</b>, name: String"
  )

  @Test
  def nestedCallInnerFunction(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(x: Int, y: Int) = x + y
         |  def bar(s: String): Int = s.length
         |  foo(bar(..$CARET), 2)
         |}""".stripMargin,
    expectedHintText = "<b>s: String</b>"
  )

  @Test
  def noParamInfoOutsideCall(): Unit = checkNoParamInfoCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )

  @Test
  def noParamInfoAfterClosingParen(): Unit = checkNoParamInfoCommandCompletion(
    s"""object Test {
       |  def foo(x: Int) = x
       |  foo(1).$CARET
       |}""".stripMargin
  )

  @Test
  def typeParamSingle(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Container[T]
         |
         |object Test {
         |  new Container[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>T</b>"
  )

  @Test
  def typeParamsTwoFirstPosition(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Pair[A, B]
         |
         |object Test {
         |  new Pair[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>A</b>, B"
  )

  @Test
  def typeParamsTwoSecondPosition(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Pair[A, B]
         |
         |object Test {
         |  new Pair[Int, ..$CARET]
         |}""".stripMargin,
    expectedHintText = "A, <b>B</b>"
  )

  @Test
  def typeParamCovariant(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Box[+T]
         |
         |object Test {
         |  new Box[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>+T</b>"
  )

  @Test
  def typeParamContravariant(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Sink[-T]
         |
         |object Test {
         |  new Sink[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>-T</b>"
  )

  @Test
  def typeParamUpperBound(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Bounded[T <: AnyRef]
         |
         |object Test {
         |  new Bounded[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>T &lt;: AnyRef</b>" // encoded T <: AnyRef
  )

  @Test
  def typeParamLowerBound(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""class Floored[T >: Int]
         |
         |object Test {
         |  new Floored[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>T &gt;: Int</b>" // encoded T >: Int
  )

  @Test
  def typeParamGenericMethodSingleParam(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def wrap[T](x: T): Option[T] = Some(x)
         |  wrap[..$CARET](42)
         |}""".stripMargin,
    expectedHintText = "<b>T</b>"
  )

  @Test
  def typeParamGenericMethodTwoParams(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def transform[A, B](x: A)(f: A => B): B = f(x)
         |  transform[..$CARET]
         |}""".stripMargin,
    expectedHintText = "<b>A</b>, B"
  )

  @Test
  def typeParamGenericMethodSecondTypeParam(): Unit = doParamInfoCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def transform[A, B](x: A)(f: A => B): B = f(x)
         |  transform[Int, ..$CARET]
         |}""".stripMargin,
    expectedHintText = "A, <b>B</b>"
  )

  @Test
  def noTypeParamInfoAfterClosingBracket(): Unit = checkNoParamInfoCommandCompletion(
    s"""class Container[T]
       |
       |object Test {
       |  new Container[Int].$CARET
       |}""".stripMargin
  )
}
