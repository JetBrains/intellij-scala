package org.jetbrains.plugins.scala.lang.breadcrumbs

import com.intellij.ide.ui.UISettings
import org.intellij.lang.annotations.{Language => Lang}
import com.intellij.lang.Language
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.xml.breadcrumbs.BreadcrumbsUtilEx
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.{RevertableChange, CommonScalaRevertableChanges}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage, ScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused
import scala.jdk.CollectionConverters.CollectionHasAsScala

@RunWith(classOf[JUnitParamsRunner])
abstract class ScalaBreadcrumbsTestBase extends ScalaLightCodeInsightFixtureTestCase {
  private def extractBreadCrumbsAtCaret(code: String): Iterable[Crumb] = {
    scalaFixture.configureFromFileText(code.replace("/*caret*/", "<caret>"))
    scalaFixture.javaFixture.getBreadcrumbsAtCaret.asScala
  }

  protected def doTest(@Lang("Scala") code: String, expectedComponents: String*): Unit = {
    val breadcrumbs = extractBreadCrumbsAtCaret(code)
    val actualComponents = breadcrumbs.map(_.getText).toArray
    assertOrderedEquals(actualComponents, expectedComponents: _*)
  }

  protected def doTestTooltip(@Lang("Scala") code: String, expectedTooltips: String*): Unit = {
    val breadcrumbs = extractBreadCrumbsAtCaret(code)
    val actualTooltips = breadcrumbs.map(_.getTooltip).toArray
    assertOrderedEquals(actualTooltips, expectedTooltips: _*)
  }

  @Test
  @Parameters(method = "breadcrumbsDefaultVisibilityParams")
  @TestCaseName("{method}[{0}, showingMembersInNavBar = {1}]")
  def testBreadcrumbsDefaultVisibility(lang: Language, showMembersInNavBar: Boolean): Unit =
    RevertableChange.withModifiedSetting(UISettings.getInstance())(showMembersInNavBar)(
      _.getShowMembersInNavigationBar,
      _.setShowMembersInNavigationBar(_)
    ).run {
      UISettings.getInstance().fireUISettingsChanged()
      EditorSettingsExternalizable.getInstance().resetDefaultBreadcrumbVisibility()

      val expected = !showMembersInNavBar
      val actual = BreadcrumbsUtilEx.isBreadcrumbsShownFor(lang)
      assertEquals(expected, actual)
    }

  protected def breadcrumbsDefaultVisibilityTestLanguages: Seq[Language]

  @unused("used reflectively by the @Parameters annotation")
  private def breadcrumbsDefaultVisibilityParams: Array[Array[Any]] = {
    val params = for {
      lang <- breadcrumbsDefaultVisibilityTestLanguages
      showMembersInNavBar <- Seq(true, false)
    } yield Array(lang, showMembersInNavBar)
    params.toArray
  }
}

class ScalaBreadcrumbsTest extends ScalaBreadcrumbsTestBase {
  override protected def breadcrumbsDefaultVisibilityTestLanguages: Seq[Language] = Seq(
    ScalaLanguage.INSTANCE,
    Scala3Language.INSTANCE
  )

  protected def withMatchEnabled(body: => Unit): Unit =
    CommonScalaRevertableChanges.withModifiedScalaProjectSettings[Boolean](
      getProject,
      _.isBreadcrumbsMatchEnabled,
      _.setBreadcrumbsMatchEnabled(_),
      true
    ).run(body)

  protected def withIfDoWhileEnabled(body: => Unit): Unit =
    CommonScalaRevertableChanges.withModifiedScalaProjectSettings[Boolean](
      getProject,
      _.isBreadcrumbsIfDoWhileEnabled,
      _.setBreadcrumbsIfDoWhileEnabled(_),
      true
    ).run(body)

  protected def withValDefEnabled(body: => Unit): Unit =
    CommonScalaRevertableChanges.withModifiedScalaProjectSettings[Boolean](
      getProject,
      _.isBreadcrumbsValDefEnabled,
      _.setBreadcrumbsValDefEnabled(_),
      true
    ).run(body)

  // class/trait/object definitions

  @Test
  def testClass(): Unit = doTest(
    """class MyClass {
      |  /*caret*/
      |}""".stripMargin,
    "MyClass"
  )

  @Test
  def testTrait(): Unit = doTest(
    """trait MyTrait {
      |  /*caret*/
      |}""".stripMargin,
    "MyTrait"
  )

  @Test
  def testObject(): Unit = doTest(
    """object MyObject {
      |  /*caret*/
      |}""".stripMargin,
    "MyObject"
  )

  @Test
  def testMethodInNestedClasses(): Unit = doTest(
    """object Outer {
      |  class Inner {
      |    def method(): Unit = {
      |      /*caret*/
      |    }
      |  }
      |}""".stripMargin,
    "Outer", "Inner", "method()"
  )

  @Test
  def testNewExpression(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    new Thread {
      |      /*caret*/
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "new Thread"
  )

  // describeNewTemplate: None branch — no explicit parent → "new Object"
  @Test
  def testNewExpressionNoParent(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    new {
      |      val x: Int = 42
      |      /*caret*/
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "new Object"
  )

  // function definitions

  @Test
  def testMethod(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    /*caret*/
      |  }
      |}""".stripMargin,
    "MyClass", "foo()"
  )

  @Test
  def testMethodWithParams(): Unit = doTest(
    """class MyClass {
      |  def foo(x: Int, y: String): Unit = {
      |    /*caret*/
      |  }
      |}""".stripMargin,
    "MyClass", "foo(x: Int, y: String)"
  )

  @Test
  def testConstructor(): Unit = doTest(
    """class MyClass {
      |  def this(x: Int) = {
      |    this()
      |    val z = x/*caret*/
      |  }
      |}""".stripMargin,
    "MyClass", "this(x: Int)"
  )

  @Test
  def testLambda(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    val f = (x: Int) => {
      |      /*caret*/
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "λ(x: Int)"
  )

  // match/case (disabled by default)

  @Test
  def testMatchExpression(): Unit = withMatchEnabled {
    doTest(
      """class MyClass {
        |  def foo(x: Int): String = x match {
        |    case 1 => /*caret*/"one"
        |    case _ => "other"
        |  }
        |}""".stripMargin,
      "MyClass", "foo(x: Int)", "x match {...}", "case 1 =>"
    )
  }

  // if/while/do-while (disabled by default)

  @Test
  def testIfExpression(): Unit = withIfDoWhileEnabled {
    doTest(
      """class MyClass {
        |  def foo(x: Int): Unit = {
        |    if (x > 0) {
        |      /*caret*/
        |    }
        |  }
        |}""".stripMargin,
      "MyClass", "foo(x: Int)", "if (x > 0) {...}"
    )
  }

  @Test
  def testWhileLoop(): Unit = withIfDoWhileEnabled {
    doTest(
      """class MyClass {
        |  def foo(): Unit = {
        |    while (true) {
        |      /*caret*/
        |    }
        |  }
        |}""".stripMargin,
      "MyClass", "foo()", "while(true)"
    )
  }

  @Test
  def testDoWhileLoop(): Unit = withIfDoWhileEnabled {
    doTest(
      """class MyClass {
        |  def foo(): Unit = {
        |    do {
        |      /*caret*/
        |    } while (true)
        |  }
        |}""".stripMargin,
      "MyClass", "foo()", "do ... while(true)"
    )
  }

  // val/var definitions (disabled by default)

  @Test
  def testValDefinition(): Unit = withValDefEnabled {
    doTest(
      """class MyClass {
        |  val myVal: Int = {
        |    /*caret*/42
        |  }
        |}""".stripMargin,
      "MyClass", "val myVal"
    )
  }

  // note: variable definition is not handled at the moment
  // it is a ScMember, but it is not a ScNamedElement, .getName on it returns `null`
  @Test
  def testVarDefinition(): Unit = withValDefEnabled {
    doTest(
      """class MyClass {
        |  var counter: Int = /*caret*/0
        |}""".stripMargin,
      "MyClass", null
    )
  }

  // type aliases (note: currently falls under ScMember branch and is only shown if isBreadcrumbsValDefEnabled=true)

  @Test
  def testTypeAlias(): Unit = withValDefEnabled {
    doTest(
      """class MyClass {
        |  type MyAlias = /*caret*/Int
        |}""".stripMargin,
      "MyClass", "MyAlias"
    )
  }

  // Type parameters are not shown in the breadcrumb — only value parameter types are shown.
  @Test
  def testMethodWithTypeParam(): Unit = doTest(
    """class MyClass {
      |  def foo[T](x: T): T = {
      |    /*caret*/x
      |  }
      |}""".stripMargin,
    "MyClass", "foo(x: T)"
  )

  // truncation: parameter/condition strings >= 25 chars are replaced with (...)

  @Test
  def testLongMethodSignature(): Unit = doTest(
    """class MyClass {
      |  def process(firstParam: Int, secondParam: Int): Unit = {
      |    /*caret*/
      |  }
      |}""".stripMargin,
    "MyClass", "process(...)"
  )

  @Test
  def testConstructorLongSignature(): Unit = doTest(
    """class MyClass {
      |  def this(firstParam: Int, secondParam: Int) = {
      |    this(/*caret*/)
      |  }
      |}""".stripMargin,
    "MyClass", "this(...)"
  )

  @Test
  def testLongMatchSubject(): Unit = withMatchEnabled {
    // "currentHttpRequestContext" is exactly 25 chars, which hits the truncation threshold
    doTest(
      """class MyClass {
        |  def foo(): Unit = {
        |    val currentHttpRequestContext = 0
        |    currentHttpRequestContext match {
        |      case _ => /*caret*/
        |    }
        |  }
        |}""".stripMargin,
      "MyClass", "foo()", "(...) match {...}", "case _ =>"
    )
  }

  @Test
  def testLongIfCondition(): Unit = withIfDoWhileEnabled {
    doTest(
      """class MyClass {
        |  def foo(): Unit = {
        |    val currentHttpRequestContext = 0
        |    if (currentHttpRequestContext > 0) {
        |      /*caret*/
        |    }
        |  }
        |}""".stripMargin,
      "MyClass", "foo()", "if ((...)) {...}"
    )
  }

  // lambda: no params, nested lambdas, match inside lambda

  @Test
  def testLambdaNoParams(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    val f = () => {
      |      /*caret*/
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "λ()"
  )

  @Test
  def testNestedLambdas(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    val outer = (x: Int) => {
      |      val inner = (y: String) => {
      |        /*caret*/
      |      }
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "λ(x: Int)", "λ(y: String)"
  )

  @Test
  def testMatchInsideLambda(): Unit = withMatchEnabled {
    doTest(
      """class MyClass {
        |  def foo(): Unit = {
        |    val handler = (x: Int) => x match {
        |      case 0 => /*caret*/"zero"
        |      case _ => "other"
        |    }
        |  }
        |}""".stripMargin,
      "MyClass", "foo()", "λ(x: Int)", "x match {...}", "case 0 =>"
    )
  }

  @Test
  def testLambdaMultipleParams(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    val f = (x: Int, y: String) => {
      |      /*caret*/
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "λ(x: Int, y: String)"
  )

  // val: multiple bindings (note: there is a missing closing paren in the current behavior (mkString uses "" as suffix))
  @Test
  def testMultipleValBindings(): Unit = withValDefEnabled {
    doTest(
      """class MyClass {
        |  val (a, b, c) = /*caret*/(1, 2, 3)
        |}""".stripMargin,
      "MyClass", "val (a, b, c"
    )
  }

  @Test
  def testNewExpressionWithMixin(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    new Runnable with Serializable {
      |      /*caret*/
      |      override def run(): Unit = ()
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "new Runnable with ..."
  )

  @Test
  def testNewExpressionWithConstructorArgs(): Unit = doTest(
    """class MyClass {
      |  def foo(): Unit = {
      |    new StringBuilder("hello") {
      |      /*caret*/
      |      override def toString: String = "custom"
      |    }
      |  }
      |}""".stripMargin,
    "MyClass", "foo()", "new StringBuilder(...)"
  )

  @Test
  def testLongSingleValBindingName(): Unit = withValDefEnabled {
    doTest(
      """class MyClass {
        |  val myVeryLongVariableNameHere: Int = /*caret*/0
        |}""".stripMargin,
      "MyClass", "val (...)"
    )
  }

  @Test
  def testLongCasePattern(): Unit = withMatchEnabled {
    doTest(
      """class MyClass {
        |  def foo(xs: List[Int]): Unit = xs match {
        |    case first :: second :: third :: rest => /*caret*/
        |    case Nil => ()
        |  }
        |}""".stripMargin,
      "MyClass", "foo(xs: List[Int])", "xs match {...}", "case (...) =>"
    )
  }

  // tooltip tests (getElementTooltip)

  @Test
  def testTooltipClass(): Unit = doTestTooltip(
    """class MyClass {
      |  /*caret*/
      |}""".stripMargin,
    "class MyClass"
  )

  @Test
  def testTooltipTrait(): Unit = doTestTooltip(
    """trait MyTrait {
      |  /*caret*/
      |}""".stripMargin,
    "trait MyTrait"
  )

  @Test
  def testTooltipObject(): Unit = doTestTooltip(
    """object MyObject {
      |  /*caret*/
      |}""".stripMargin,
    "object MyObject"
  )

  @Test
  def testTooltipAbstractFunction(): Unit = doTestTooltip(
    """trait MyTrait {
      |  def abstractFoo(x: Int): /*caret*/Int
      |}""".stripMargin,
    "trait MyTrait",
    "abstractFoo(x: Int)"
  )

  @Test
  def testTooltipConcreteFunction(): Unit = doTestTooltip(
    """class MyClass {
      |  def double(x: Int): Int = x * /*caret*/2
      |}""".stripMargin,
    "class MyClass",
    "double(x: Int)"
  )

  @Test
  def testTooltipLambda(): Unit = doTestTooltip(
    """class MyClass {
      |  val f = (x: Int) => x * /*caret*/2
      |}""".stripMargin,
    "class MyClass",
    "(x: Int) => x * 2"
  )

  @Test
  def testTooltipValMember(): Unit = withValDefEnabled {
    doTestTooltip(
      """object MyObject {
        |  val myVal: Int = /*caret*/42
        |}""".stripMargin,
      "object MyObject",
      "val myVal: Int = 42"
    )
  }

  @Test
  def testTooltipIfExpressionIsQuestionMark(): Unit = withIfDoWhileEnabled {
    doTestTooltip(
      """object MyObj {
        |  def f() = if (true) /*caret*/()
        |}""".stripMargin,
      "object MyObj",
      "f()",
      "?"
    )
  }

  @Test
  def testTooltipMatchAndCaseClause(): Unit = withMatchEnabled {
    doTestTooltip(
      """object MyObj {
        |  def f(x: Int) = x match { case _ => /*caret*/() }
        |}""".stripMargin,
      "object MyObj",
      "f(x: Int)",
      "?",
      "case _ => ()"
    )
  }
}

class Scala3BreadcrumbsTest extends ScalaBreadcrumbsTest {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected def doTest(@Lang("Scala 3") code: String, expectedComponents: String*): Unit =
    super.doTest(code, expectedComponents: _*)

  override protected def doTestTooltip(@Lang("Scala 3") code: String, expectedTooltips: String*): Unit =
    super.doTestTooltip(code, expectedTooltips: _*)

  // enums

  @Test
  def testEnum(): Unit = doTest(
    """enum Color {
      |  case Red, Green, Blue
      |
      |  def describe: String = {
      |    /*caret*/this.toString
      |  }
      |}""".stripMargin,
    "Color", "describe()"
  )

  @Test
  def testEnumWithMatch(): Unit = withMatchEnabled {
    doTest(
      """enum Direction {
        |  case North, South, East, West
        |
        |  def opposite: Direction = this match {
        |    case North => /*caret*/South
        |    case South => North
        |    case East  => West
        |    case West  => East
        |  }
        |}""".stripMargin,
      "Direction", "opposite()", "this match {...}", "case North =>"
    )
  }

  // extensions (extension block is not shown since isBreadcrumbsValDefEnabled is false by default)

  @Test
  def testExtensionMethod(): Unit = doTest(
    """object MyObj {
      |  extension (s: String) {
      |    def greet: String = {
      |      /*caret*/"Hello"
      |    }
      |  }
      |}""".stripMargin,
    "MyObj", "greet()"
  )

  @Test
  def testExtensionMethodWithParams(): Unit = doTest(
    """object MyObj {
      |  extension (s: String) {
      |    def repeat(n: Int): String = {
      |      /*caret*/s * n
      |    }
      |  }
      |}""".stripMargin,
    "MyObj", "repeat(n: Int)"
  )

  // givens

  @Test
  def testGivenDefinition(): Unit = doTest(
    """trait MyTrait {
      |  def foo: Int
      |}
      |
      |given myGiven: MyTrait with {
      |  def foo: Int = /*caret*/42
      |}""".stripMargin,
    "myGiven", "foo()"
  )

  @Test
  def testGivenAlias(): Unit = doTest(
    """class MyClass {
      |  given myGiven: Int = /*caret*/42
      |}""".stripMargin,
    "MyClass", "myGiven()"
  )

  // getTemplateDefTooltip: enum → "enum X"

  @Test
  def testTooltipEnum(): Unit = doTestTooltip(
    """enum Color {
      |  case Red, Green, Blue
      |  /*caret*/
      |}""".stripMargin,
    "enum Color"
  )
}
