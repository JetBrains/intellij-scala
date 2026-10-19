package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.extensions.{StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.junit.Assert.assertNotNull

import scala.concurrent.duration.DurationInt

class AddParametersQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected def description: String = ""

  override protected def descriptionMatches(s: String): Boolean = s.startsWith("Too many arguments")

  override protected def testQuickFix(text: String, expected: String, hint: String): Unit = {
    val action = doFindQuickFixes(text, hint).head
    val expectedText = expected.withNormalizedSeparator.trim.replace(CARET, "")

    executeWriteActionCommand() {
      action.invoke(getProject, getEditor, getFile)
    }(using getProject)
    AwaitTestUtils.waitForConditionDispatchingEdtEventsOrFail(5.seconds, "Quick fix did not finish opening the editor") { () =>
      getFile.getText.withNormalizedSeparator.trim == expectedText
    }

    myFixture.checkResult(expected.withNormalizedSeparator.trim, true)
  }

  def testStartsTemplateAfterOpeningEditor(): Unit = {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable)

    val action = doFindQuickFixes(
      s"""
         |object Foo {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(true,$CARET false)
         |}
    """.stripMargin,
      "Add parameter to method 'doSomething'"
    ).head

    executeWriteActionCommand() {
      action.invoke(getProject, getEditor, getFile)
    }(using getProject)
    AwaitTestUtils.waitForConditionDispatchingEdtEventsOrFail(5.seconds, "Live template was not started") { () =>
      TemplateManagerImpl.getTemplateState(getEditor) != null
    }

    val templateState = TemplateManagerImpl.getTemplateState(getEditor)
    assertNotNull("Template state should not be null", templateState)

    myFixture.`type`("enabled")
    executeWriteActionCommand() {
      templateState.gotoEnd(false)
    }(using getProject)

    myFixture.checkResult(
      """object Foo {
        |  def doSomething(flag: Boolean, enabled: Boolean) {}
        |
        |  doSomething(true, false)
        |}""".stripMargin
    )
  }

  def test1ArgToMuch(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true,$CARET false)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Boolean)$CARET {}
       |
       |  doSomething(true, false)
       |}
    """.stripMargin,
    "Add parameter to method 'doSomething'"
  )

  def test2ArgsToMunch(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true,$CARET 2, false)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Int, arg2: Boolean)$CARET {}
       |
       |  doSomething(true, 2, false)
       |}
    """.stripMargin,
    "Add parameters to method 'doSomething'"
  )

  def testListArg(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  val list: List[String] = ???
       |
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true, ${CARET}list)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  val list: List[String] = ???
       |
       |  def doSomething(flag: Boolean, arg1: List[String])$CARET {}
       |
       |  doSomething(true, list)
       |}
    """.stripMargin,
    "Add parameter to method 'doSomething'"
  )

  def testCaseClassArg(): Unit = testQuickFix(
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true,$CARET Foo(1))
       |}
    """.stripMargin,
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Foo)$CARET {}
       |
       |  doSomething(true, Foo(1))
       |}
    """.stripMargin,
    "Add parameter to method 'doSomething'"
  )

  def testNoArgYet(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def doSomething() {}
       |
       |  doSomething(${CARET}true)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  def doSomething(arg1: Boolean)$CARET {}
       |
       |  doSomething(true)
       |}
    """.stripMargin,
    "Add parameter to method 'doSomething'"
  )

  def testFunType(): Unit = testQuickFix(
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean, n: Int) {}
       |
       |  val r = (x: Int) => Foo(x)
       |  doSomething(true, r,$CARET Foo(1), 1)
       |}
    """.stripMargin,
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Int => Foo, arg2: Foo, n: Int)$CARET {}
       |
       |  val r = (x: Int) => Foo(x)
       |  doSomething(true, r, Foo(1), 1)
       |}
    """.stripMargin,
    "Add parameters to method 'doSomething'"
  )

  def testMultipleParamLists(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def test(a: Int)(b: Int) = 0
       |
       |  test(3)(3,$CARET 3)
       |}
       |""".stripMargin,
    s"""
       |object Foo {
       |  def test(a: Int)(b: Int, arg1: Int)$CARET = 0
       |
       |  test(3)(3, 3)
       |}
       |""".stripMargin,
    "Add parameter to method 'test'"
  )

  def testMultipleParamListsWithApply(): Unit = testQuickFix(
    s"""
       |class Foo {
       |  def apply(a: Int)(b: Int) = 0
       |
       |  def test(a: Int)(b: Int): Foo = 0
       |
       |  (test(1)(2)(3))(4,$CARET 4)
       |}
       |""".stripMargin,
    s"""
       |class Foo {
       |  def apply(a: Int)(b: Int, arg1: Int)$CARET = 0
       |
       |  def test(a: Int)(b: Int): Foo = 0
       |
       |  (test(1)(2)(3))(4, 4)
       |}
       |""".stripMargin,
    "Add parameter to method 'apply'"
  )

  def testReturnedLambda(): Unit = checkIsNotAvailable(
    s"""
       |class Foo {
       |  def test(a: Int): Int => Int = 0
       |
       |  test(1)(2,$CARET 3)
       |}
       |""".stripMargin,
    "Add parameters to method"
  )

  def testFiddlingIn(): Unit = testQuickFix(
    s"""
       |class A
       |class B
       |class C
       |class D
       |class X
       |
       |object Foo {
       |  def test(b: B, x: X): Foo = 0
       |
       |  test(new A, new B, ${CARET}new C, new D)
       |}
       |""".stripMargin,
    s"""
       |class A
       |class B
       |class C
       |class D
       |class X
       |
       |object Foo {
       |  def test(arg1: A, b: B, x: X, arg2: D)${CARET}: Foo = 0
       |
       |  test(new A, new B, new C, new D)
       |}
       |""".stripMargin,
    "Add parameters to method 'test'"
  )
}
