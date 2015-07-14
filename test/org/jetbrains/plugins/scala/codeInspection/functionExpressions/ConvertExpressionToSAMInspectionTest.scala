package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaLightInspectionFixtureTestAdapter}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 6/30/15
 */
class ConvertExpressionToSAMInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.experimental = true
    defaultProfile.setSettings(newSettings)
  }

  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ConvertExpressionToSAMInspection]

  override protected def annotation: String = InspectionBundle.message("convert.expression.to.sam")

  def testThreadRunnable(): Unit = {
    val code =
      s"""
         |new Thread(${START}new Runnable {
         |override def run() = println()
         |}$END
      """.stripMargin
    check(code)
    val text =
      s"""
         |new Thread(new Runnable {
         |override def run() = println()
         |})
      """.stripMargin
    val res = "\nnew Thread(() => println())\n"
    testFix(text, res, annotation)
  }

  def testValueDefinition(): Unit = {
    val code =
      s"""
        |val y: Runnable = ${START}new Runnable {
        |  override def run(): Unit = ???
        |}$END
      """.stripMargin
    check(code)
    val text =
      """
        |val y: Runnable = new Runnable {
        |  override def run(): Unit = ???
        |}
      """.stripMargin
    val res = "\nval y: Runnable = () => ???\n"
    testFix(text, res, annotation)
  }

  def testValueDefinitionNoDeclaredType(): Unit = {
    val text =
      """
        |val y = new Runnable {
        |  override def run(): Unit = println()
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testNoParenFunction(): Unit = {
    val code =
      s"""
        |trait A {
        |  def foo(): String
        |}
        |def bar(a: A) = println()
        |bar(${START}new A {
        |  override def foo(): String = "something"
        |}$END)
      """.stripMargin
    check(code)
    val text =
      """
        |trait A {
        |  def foo(): String
        |}
        |def bar(a: A) = ???
        |bar(new A {
        |  override def foo(): String = "something"
        |})
      """.stripMargin
    def res =
      """
        |trait A {
        |  def foo(): String
        |}
        |def bar(a: A) = ???
        |bar(() => "something")
      """.stripMargin
    testFix(text, res, annotation)
  }

  def testParameterless(): Unit = {
    val code =
      """
        |trait A {
        |  def foo: String
        |}
        |def bar(a: A) = ???
        |bar(new A {
        |  def foo = "ab"
        |})
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testTwoFunctions(): Unit = {
    val code =
      """
        |trait A {
        |  def foo(): String
        |  def bar(): Int = 2
        |}
        |def baz(a: A) = println()
        |baz(new A {
        |  def foo() = "2"
        |  override def bar() = 3
        |})
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testInner(): Unit = {
    val code =
      s"""
        |new Thread(${START}new Runnable {
        |  def run() {
        |    def foo(i: Int) = i
        |
        |    println(foo(10))
        |  }
        |}$END)
      """.stripMargin
    check(code)
    val text =
      s"""
         |new Thread(new Runnable {
         |  def run() {
         |    def foo(i: Int) = i
         |
         |    println(foo(10))
         |  }
         |})
      """.stripMargin
    val res =
      s"""
         |new Thread(() => {
         |  def foo(i: Int) = i
         |
         |  println(foo(10))
         |})
      """.stripMargin
    testFix(text, res, annotation)
  }

  def testMultiLine(): Unit = {
    val code =
      s"""
        |new Thread(${START}new Runnable {
        |  override def run(): Unit = {
        |    val i = 2 + 3
        |    val z = 2
        |    println(i - z)
        |  }
        |}$END)
      """.stripMargin
    check(code)
    val text =
      s"""
         |new Thread(new Runnable {
         |  override def run(): Unit = {
         |    val i = 2 + 3
         |    val z = 2
         |    println(i - z)
         |  }
         |})
       """.stripMargin
    val res =
      """
        |new Thread(() => {
        |  val i = 2 + 3
        |  val z = 2
        |  println(i - z)
        |})
      """.stripMargin
    testFix(text, res, annotation)
  }
}
