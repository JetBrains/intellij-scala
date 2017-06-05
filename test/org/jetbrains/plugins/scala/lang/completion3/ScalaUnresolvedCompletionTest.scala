package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.lang.completion.ScalaTextLookupItem
import org.junit.Assert

/**
  * Created by Kate Ustiuzhanin on 24/03/2017.
  */
class ScalaUnresolvedCompletionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testFieldVal(): Unit = {
    val fileText =
      """
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
        |  val <caret>
        |}
      """

    doTest(normalize(fileText), Seq("intValue"), Seq("methodWithParams(s: String)", "methodWithoutParams()"))
  }


  def testFieldVar(): Unit = {
    val fileText =
      """
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
        |  var <caret>
        |}
      """

    doTest(fileText, Seq("field1", "value"), Seq("foo1(i: Int, i1: Int)", "foo2(i: Int, value: Any)"))
  }

  def testMethodWithUnresolvedParams(): Unit = {
    val fileText =
      """
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
        |  def <caret>
        |}
      """

    doTest(fileText, Seq("field1", "value", "foo1(i: Int, i1: Int)", "foo2(i: Int, value: Any)"))
  }

  def testMethod(): Unit = {
    val fileText =
      """
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
        |  def <caret>
        |}
      """

    doTest(normalize(fileText), Seq("methodWithParams(str: String)", "methodWithoutParams()", "intValue"))
  }

  def testMethodWithNamedParams(): Unit = {
    val fileText =
      """
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
        |  def <caret>
        |}
      """

    doTest(normalize(fileText),
      Seq("methodWithParams(a: String, b: Int, i: Int)", "methoda(i: Int, i1: Int, str: String, d: Double, i2: Int, str1: String)", "intValue"))
  }

  def testInfixMethodWithParams(): Unit = {
    val fileText =
      """
        |case class I(k: Int) {
        | I(1) add 3
        |
        | def <caret>
        |}
      """

    doTest(normalize(fileText), Seq("add(i: Int)"))
  }

  def testObject(): Unit = {
    val fileText =
      """
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
        |  object <caret>
        |}
      """

    doTest(normalize(fileText), Seq("intValue", "methodWithoutParams", "methodWithoutParams"))
  }

  def testObjectSelected(): Unit = {
    val fileText =
      """
        |class Test {
        |  def method(): Unit = {
        |   methodWithoutParams("Hey!")
        |   object <caret>
        |  }
        |}
      """

    val resultText =
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

    doTest(normalize(fileText), normalize(resultText))
  }

  def testCaseClass(): Unit = {
    val fileText =
      """
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
        |  case class <caret>
        |}
      """

    doTest(normalize(fileText), Seq("methodWithParams(str: String)", "methodWithoutParams()"), Seq("intValue"))
  }

  def testClass(): Unit = {

    val fileText =
      """
        |object Test{
        | sealed trait <caret>
        |
        | case class ClassWithParams(firstParam: Long, secondParam: Option[String]) extends Base
        |
        | println(foo(3, 4))
        |
        | val typedVal: NewType = "Hey!"
        |}
      """

    doTest(normalize(fileText), Seq("Base", "NewType"), Seq("foo"))
  }

  def testTypeAlias(): Unit = {
    val fileText =
      """
        |object Test{
        | type <caret>
        |
        | case class ClassWithParams(firstParam: Long, secondParam: Option[String]) extends Base
        |
        | val typedVal: NewType = "Hey!"
        |
        | println(unresolved)
        |}
      """

    doTest(normalize(fileText), Seq("Base", "NewType"), Seq("unresolved"))
  }

  def testClassAfterNew(): Unit = {
    val fileText =
      """
        |class <caret>
        |new Test(12, "hi!")
      """.stripMargin

    doTest(normalize(fileText), Seq("Test(i: Int, str: String)"))
  }

  def testRanges(): Unit = {
    val fileText =
      """
        |class X {
        |  printX(12)
        |  printX("Sfd")
        |
        |  case class I(k: Int) {
        |    def addd(i: Int) = ???
        |
        |    Seq(3, 4) tail
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
        |    def <caret>
        |  }
        |}
      """

    doTest(
      fileText,
      Seq("field1", "foo1(i: Int, i1: Int)", "foo2(i: Int, value: Any)", "value"),
      Seq("printX(str: String)", "printX(i: Int)")
    )
  }

  def testNoCompletionAfterOverrideField(): Unit = noCompletion("override val <caret> }", Seq("variable"))

  def testNoCompletionAfterOverrideClazz(): Unit = noCompletion("override class <caret> }", Seq("TestType"))

  def testNoCompletionAfterOverrideType(): Unit = noCompletion("override type <caret> }", Seq("TestType"))

  def testNoCompletionAfterOverrideMethod(): Unit = noCompletion("override def <caret> }", Seq("method(i: Int, str: String)"))

  private def noCompletion(text: String, excluded: Seq[String]): Unit = {
    val fileBaseText =
      """
        |class Test {
        |  variable + 3
        |
        |  method(12, "test")
        |
        |  val t: TestType = _
        |
      """

    doTest(fileBaseText + text, Seq.empty, excluded)
  }

  private def doHighlighting(fileText: String) = {
    val fixture = getFixture
    fixture.openFileInEditor(fixture.configureByText("dummy.scala", normalize(fileText)).getVirtualFile)
    fixture.doHighlighting(HighlightSeverity.ERROR)
    fixture
  }

  private def doTest(fileText: String, includedSeq: Seq[String], excludedSeq: Seq[String] = Seq.empty): Unit = {
    doHighlighting(fileText)

    val actual =
      getFixture
        .completeBasic()
        .filter(_.isInstanceOf[ScalaTextLookupItem])
        .map(_.getLookupString)
        .sorted

    assertContaints(actual, excludedSeq)
    Assert.assertEquals(null, includedSeq.sorted.mkString("\n"), actual.mkString("\n"))
  }

  private def doTest(fileText: String, resultText: String): Unit = {
    doHighlighting(fileText)

    getFixture.completeBasic()
    getFixture.finishLookup('\t')
    getFixture.checkResult(resultText)
  }

  private def assertContaints(expected: Seq[String], excluded: Seq[String]): Unit = {
    val intersection = expected.intersect(excluded)
    assert(intersection.isEmpty, "Completion list contains unexpected elements:\n" + intersection.mkString("\n"))
  }
}
