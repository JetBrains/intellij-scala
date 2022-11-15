package org.jetbrains.plugins.scala.codeInsight.generation.actions

import com.intellij.lang.LanguageCodeInsightActionHandler
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

trait GenerateCompanionObjectTestBase extends ScalaGenerateTestBase {
  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGenerateCompanionObjectAction.Handler
}

class GenerateCompanionObjectTest extends GenerateCompanionObjectTestBase {

  def testCompanionObjectExist(): Unit = {
    val text =
      s"""case class A(x: Int, s: String) {
         |  def foo() {}
         |  $CARET
         |}
         |
         |object A {}
         |""".stripMargin
    checkIsNotAvailable(text)
  }

  def testInObject(): Unit = {
    val text =
      s"""object A { $CARET
         |  def foo() {}
         |  val bar = 1
         |}""".stripMargin
    checkIsNotAvailable(text)
  }

  def testInAnonymous(): Unit = {
    val text =
      s"""object A {
         |  val runnable = new Runnable {
         |    def run() {} $CARET
         |  }
         |}""".stripMargin
    checkIsNotAvailable(text)
  }

  def testCaseClass(): Unit = {
    val text =
      s"""case class A(x: Int, s: String) {
         |  def foo() {}
         |$CARET
         |}""".stripMargin

    val result =
      s"""case class A(x: Int, s: String) {
         |  def foo() {}
         |
         |}
         |
         |object A {
         |  $CARET
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testClass(): Unit = {
    val text =
      s"""class A(x: Int, s: String) {
         |  def foo() {}
         |$CARET
         |}""".stripMargin

    val result =
      s"""class A(x: Int, s: String) {
         |  def foo() {}
         |
         |}
         |
         |object A {
         |  $CARET
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testTrait(): Unit = {
    val text =
      s"""trait A {
         |  def foo() {$CARET}
         |
         |}""".stripMargin

    val result =
      s"""trait A {
         |  def foo() {}
         |
         |}
         |
         |object A {
         |  $CARET
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testInnerClass(): Unit = {
    val text =
      s"""trait A {
         |  def foo()
         |  class B {
         |    def bar()$CARET = 1
         |  }
         |}""".stripMargin
    val result =
      s"""trait A {
         |  def foo()
         |  class B {
         |    def bar() = 1
         |  }
         |
         |  object B {
         |    $CARET
         |  }
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class GenerateCompanionObjectTest_3_Latest extends GenerateCompanionObjectTestBase {
  private def doTest(text: String, result: String, useIndentationBasedSyntax: Boolean): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(getProject)
    val oldSetting = settings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    try {
      settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = useIndentationBasedSyntax
      performTest(text, result, checkAvailability = true, checkCaretOffset = true)
    } finally settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = oldSetting
  }

  def testClassBraceless(): Unit = {
    val text =
      s"""class C$CARET:
         |  def foo = ???
         |""".stripMargin
    val result =
      s"""class C:
         |  def foo = ???
         |
         |object C:
         |  $CARET
         |end C""".stripMargin

    doTest(text, result, useIndentationBasedSyntax = true)
  }

  def testClassBraced(): Unit = {
    val text =
      s"""class C$CARET:
         |  def foo = ???
         |""".stripMargin
    val result =
      s"""class C:
         |  def foo = ???
         |
         |object C {
         |  $CARET
         |}""".stripMargin

    doTest(text, result, useIndentationBasedSyntax = false)
  }

  def testTraitBraceless(): Unit = {
    val text =
      s"""trait T$CARET:
         |  def foo: Int
         |""".stripMargin
    val result =
      s"""trait T:
         |  def foo: Int
         |
         |object T:
         |  $CARET
         |end T""".stripMargin

    doTest(text, result, useIndentationBasedSyntax = true)
  }

  def testTraitBraced(): Unit = {
    val text =
      s"""trait T$CARET:
         |  def foo: Int
         |""".stripMargin
    val result =
      s"""trait T:
         |  def foo: Int
         |
         |object T {
         |  $CARET
         |}""".stripMargin

    doTest(text, result, useIndentationBasedSyntax = false)
  }

  def testEnumBraceless(): Unit = {
    val text =
      s"""enum E$CARET:
         |  case A, B
         |  case C(i: Int)
         |""".stripMargin
    val result =
      s"""enum E:
         |  case A, B
         |  case C(i: Int)
         |
         |object E:
         |  $CARET
         |end E""".stripMargin

    doTest(text, result, useIndentationBasedSyntax = true)
  }

  def testEnumBraced(): Unit = {
    val text =
      s"""enum E$CARET:
         |  case A, B
         |  case C(i: Int)
         |""".stripMargin
    val result =
      s"""enum E:
         |  case A, B
         |  case C(i: Int)
         |
         |object E {
         |  $CARET
         |}""".stripMargin

    doTest(text, result, useIndentationBasedSyntax = false)
  }
}
