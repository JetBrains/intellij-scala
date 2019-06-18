package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 6/30/15
  */
class ConvertExpressionToSAMInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.experimental = true
    defaultProfile.setSettings(newSettings)
  }

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ConvertExpressionToSAMInspection]

  override protected val description: String = InspectionBundle.message("convert.expression.to.sam")

  def testOverloads(): Unit = {
    val code =
      """
        |object Bug1 {
        |
        |  trait SAM1 {
        |    def foo(): Int
        |  }
        |
        |  trait SAM2 {
        |    def foo(): String
        |  }
        |
        |  def foo(s: SAM1): Unit = {
        |
        |  }
        |
        |  def foo(s: SAM2): Unit = {
        |
        |  }
        |
        |  foo(new SAM1 {
        |    override def foo(): Int = 2
        |  })
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testReturn(): Unit = {
    val code =
      """
        |trait A {
        |  def foo(): String
        |}
        |
        |def foo(): Unit = {
        |  val s: A = new A {
        |    override def foo(): String = {
        |      if (true) return ""
        |      println("AAA")
        |      "A"
        |    }
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testInfix(): Unit = {
    val code =
      s"""
         |object Koo {
         |  def foo(r: Runnable) = r.run()
         |}
         |Koo foo ${START}new Runnable $END{
         |  override def run(): Unit = ???
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |object Koo {
        |  def foo(r: Runnable) = r.run()
        |}
        |Koo foo new Runnable {
        |  override def run(): Unit = ???
        |}
      """.stripMargin
    val after =
      """
        |object Koo {
        |  def foo(r: Runnable) = r.run()
        |}
        |Koo foo (() => ???)
      """.stripMargin
    testQuickFix(before, after, description)
  }

  def testThreadRunnable(): Unit = {
    val code =
      s"""
         |new Thread(${START}new Runnable $END{
         |override def run() = println()
         |}
      """.stripMargin
    checkTextHasError(code)
    val text =
      s"""
         |new Thread(new Runnable {
         |override def run() = println()
         |})
      """.stripMargin
    val res = "\nnew Thread(() => println())\n"
    testQuickFix(text, res, description)
  }

  def testValueDefinition(): Unit = {
    val code =
      s"""
         |val y: Runnable = ${START}new Runnable $END{
         |  override def run(): Unit = ???
         |}
      """.stripMargin
    checkTextHasError(code)
    val text =
      """
        |val y: Runnable = new Runnable {
        |  override def run(): Unit = ???
        |}
      """.stripMargin
    val res = "\nval y: Runnable = () => ???\n"
    testQuickFix(text, res, description)
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
         |bar(${START}new A $END{
         |  override def foo(): String = "something"
         |})
      """.stripMargin
    checkTextHasError(code)
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

    testQuickFix(text, res, description)
  }

  def testSealedTrait(): Unit = {
    val code =
      s"""
         |sealed trait A {
         |  def foo(): String
         |}
         |def bar(a: A) = println()
         |bar(new A {
         |  override def foo(): String = "something"
         |})
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testFinalClass(): Unit = {
    val code =
      s"""
         |final class A {
         |  def foo(): String
         |}
         |def bar(a: A) = println()
         |bar(new A {
         |  override def foo(): String = "something"
         |})
      """.stripMargin
    checkTextHasNoErrors(code)
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
         |new Thread(${START}new Runnable $END{
         |  def run() {
         |    def foo(i: Int) = i
         |
        |    println(foo(10))
         |  }
         |})
      """.stripMargin
    checkTextHasError(code)
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
    testQuickFix(text, res, description)
  }

  def testMultiLine(): Unit = {
    val code =
      s"""
         |new Thread(${START}new Runnable $END{
         |  override def run(): Unit = {
         |    val i = 2 + 3
         |    val z = 2
         |    println(i - z)
         |  }
         |})
      """.stripMargin
    checkTextHasError(code)
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
    testQuickFix(text, res, description)
  }

  def testByNameAndDefaultParams(): Unit = {
    val code =
      s"""trait SAM { def test(s: => String, x: Int = 0): Unit }
         |
         |val sm: SAM = ${START}new SAM $END{
         |  override def test(s: => String, x: Int = 1): Unit = println(s)
         |}
      """.stripMargin
    checkTextHasError(code)
    val text =
      """trait SAM { def test(s: => String, x: Int = 0): Unit }
        |
        |val sm: SAM = new SAM {
        |  override def test(s: => String, x: Int = 1): Unit = println(s)
        |}
      """.stripMargin

    def res =
      """trait SAM { def test(s: => String, x: Int = 0): Unit }
        |
        |val sm: SAM = (s: String, x: Int) => println(s)
      """.stripMargin

    testQuickFix(text, res, description)
  }

  def testExistentialTypes(): Unit = {
    val code =
      s"""
         |object Foo {
         |  new MyObservable[String].addListener(${START}new MyChangeListener[String] $END{
         |    override def changed(observable: MyObservable[_ <: String], oldValue: String, newValue: String): Unit = ???
         |  })
         |}
         |
        |trait MyChangeListener[T] {
         |  def changed(observable: MyObservable[_ <: T], oldValue: T, newValue: T)
         |}
         |
        |class MyObservable[T] {
         |  def addListener (listener: MyChangeListener[_ >: T]) = ???
         |}
      """.stripMargin
    checkTextHasError(code)
    val text =
      s"""
         |object Foo {
         |  new MyObservable[String].addListener(new MyChangeListener[String] {
         |    override def changed(observable: MyObservable[_ <: String], oldValue: String, newValue: String): Unit = ???
         |  })
         |}
         |
        |trait MyChangeListener[T] {
         |  def changed(observable: MyObservable[_ <: T], oldValue: T, newValue: T)
         |}
         |
        |class MyObservable[T] {
         |  def addListener (listener: MyChangeListener[_ >: T]) = ???
         |}
      """.stripMargin
    val res =
      """
        |object Foo {
        |  new MyObservable[String].addListener((observable: MyObservable[_ <: String], oldValue: String, newValue: String) => ???)
        |}
        |
        |trait MyChangeListener[T] {
        |  def changed(observable: MyObservable[_ <: T], oldValue: T, newValue: T)
        |}
        |
        |class MyObservable[T] {
        |  def addListener (listener: MyChangeListener[_ >: T]) = ???
        |}
      """.stripMargin
    testQuickFix(text, res, description)
  }

  def testHasExpressions(): Unit = {
    val code =
      s"""
         |new Thread(${START}new Runnable $END{
         |
         |  println("Creating runnable")
         |
         |  def run() {
         |    println(foo(10))
         |  }
         |})
      """.stripMargin
    checkTextHasNoErrors(code)
  }
}
