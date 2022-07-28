package org.jetbrains.plugins.scala
package codeInsight
package delegate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
class ScalaDelegateMethodTest extends base.ScalaLightPlatformCodeInsightTestCaseAdapter
  with ScalaDelegateMethodTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}
  import ScalaDelegateMethodTestBase._

  private def doTest(fileText: String, expectedText: String,
                     settings: ScalaCodeStyleSettings = defaultSettings(getProjectAdapter)): Unit = {
    implicit val project: Project = getProjectAdapter
    configureFromFileTextAdapter("dummy.scala", fileText)

    implicit val editor: Editor = getEditorAdapter
    doTest(getFileAdapter, settings)
    checkResultByText(StringUtil.convertLineSeparators(expectedText))
  }

  def testVal(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  val d = new D
         |  $CARET
         |}""".stripMargin
    val resultText =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  val d = new D
         |
         |  def foo(x: Int): Int = d.foo(x)
         |}""".stripMargin
    doTest(text, resultText)
  }

  def testVar(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  var d = new D
         |  $CARET
         |}""".stripMargin
    val resultText =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  var d = new D
         |
         |  def foo(x: Int): Int = d.foo(x)
         |}""".stripMargin
    doTest(text, resultText)
  }

  def testDefParameterless(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  def d = new D
         |  $CARET
         |}""".stripMargin
    val resultText =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  def d = new D
         |
         |  def foo(x: Int): Int = d.foo(x)
         |}""".stripMargin
    doTest(text, resultText)
  }

  def testDefEmptyParen(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  def d() = new D
         |  $CARET
         |}""".stripMargin
    val resultText =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A {
         |  def d() = new D
         |
         |  def foo(x: Int): Int = d().foo(x)
         |}""".stripMargin
    doTest(text, resultText)
  }

  def testDelegateCompoundType(): Unit = {
    val text =
      s"""trait DT {
         |  def foo(x: Int): Int = x
         |}
         |
         |class DC {}
         |
         |class A {
         |  val d = new DC with DT
         |  $CARET
         |}""".stripMargin
    val resultText =
      s"""trait DT {
         |  def foo(x: Int): Int = x
         |}
         |
         |class DC {}
         |
         |class A {
         |  val d = new DC with DT
         |
         |  def foo(x: Int): Int = d.foo(x)
         |}""".stripMargin
    doTest(text, resultText)
  }

  def testTargetFromBaseClass(): Unit = {
    val text =
      s"""class Base {
         |  val d = new D()
         |}
         |
         |class D {
         |  def foo() {}
         |}
         |
         |class A extends Base {
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class Base {
         |  val d = new D()
         |}
         |
         |class D {
         |  def foo() {}
         |}
         |
         |class A extends Base {
         |  def foo(): Unit = d.foo()
         |}""".stripMargin
    doTest(text, result)
  }

  def testPrivateFromBaseClass(): Unit = {
    val text =
      s"""class Base {
         |  private val d = new D()
         |}
         |
         |class D {
         |  def foo() {}
         |}
         |
         |class A extends Base {
         |$CARET
         |}""".stripMargin
    val result = //no action
      s"""class Base {
         |  private val d = new D()
         |}
         |
         |class D {
         |  def foo() {}
         |}
         |
         |class A extends Base {
         |
         |}""".stripMargin
    doTest(text, result)
  }

  def testOverride(): Unit = {
    val text =
      s"""trait DT {
         |  def foo(x: Int): Int = x
         |}
         |
         |class DC extends DT
         |
         |class A extends DT {
         |  val d = new DC
         |  $CARET
         |}""".stripMargin
    val result =
      s"""trait DT {
         |  def foo(x: Int): Int = x
         |}
         |
         |class DC extends DT
         |
         |class A extends DT {
         |  val d = new DC
         |
         |  override def foo(x: Int): Int = d.foo(x)
         |}""".stripMargin
    doTest(text, result)
  }

  def testInInner(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A  {
         |  val d = new D
         |  val inner = new AnyRef {
         |    $CARET
         |  }
         |}""".stripMargin
    val result =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A  {
         |  val d = new D
         |  val inner = new AnyRef {
         |    def foo(x: Int): Int = d.foo(x)
         |  }
         |}""".stripMargin
    doTest(text, result)
  }

  def testInInner2(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A  {
         |  val inner = new AnyRef {
         |$CARET
         |  }
         |}""".stripMargin
    val result = //no effect
      s"""class D {
         |  def foo(x: Int): Int = x
         |}
         |
         |class A  {
         |  val inner = new AnyRef {
         |
         |  }
         |}""".stripMargin
    doTest(text, result)
  }

  def testMultipleParamList(): Unit = {
    val text =
      s"""class D {
         |  def foo(x: Int)(y: Int): Int = x
         |}
         |
         |class A  {
         |  val d = new D()
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class D {
         |  def foo(x: Int)(y: Int): Int = x
         |}
         |
         |class A  {
         |  val d = new D()
         |
         |  def foo(x: Int)(y: Int): Int = d.foo(x)(y)
         |}""".stripMargin
    doTest(text, result)
  }

  def testGenericDelegate(): Unit = {
    val text =
      s"""class D[T] {
         |  def foo(x: T): T = x
         |}
         |
         |class A  {
         |  val d = new D[Int]()
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class D[T] {
         |  def foo(x: T): T = x
         |}
         |
         |class A  {
         |  val d = new D[Int]()
         |
         |  def foo(x: Int): Int = d.foo(x)
         |}""".stripMargin
    doTest(text, result)
  }

  def testMethodCallNeedTypeParam(): Unit = {
    val text =
      s"""class D[T] {
         |  def foo[S <: T](x: T): T = x
         |}
         |
         |class A  {
         |  val d = new D[AnyRef]()
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class D[T] {
         |  def foo[S <: T](x: T): T = x
         |}
         |
         |class A  {
         |  val d = new D[AnyRef]()
         |
         |  def foo[S <: AnyRef](x: AnyRef): AnyRef = d.foo[S](x)
         |}""".stripMargin
    doTest(text, result)
  }

  def testNeedTypeParamWithoutRetType(): Unit = {
    val text =
      s"""class D[T] {
         |  def foo[S >: AnyRef](x: T): S = null
         |}
         |
         |class A  {
         |  val d = new D[Int]()
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class D[T] {
         |  def foo[S >: AnyRef](x: T): S = null
         |}
         |
         |class A  {
         |  val d = new D[Int]()
         |
         |  def foo[S >: AnyRef](x: Int) = d.foo[S](x)
         |}""".stripMargin
    doTest(text, result, settings = noTypeAnnotationForPublic(getProjectAdapter))
  }

  def testNoTypeParamWithReturn(): Unit = {
    val text =
      s"""class D[T] {
         |  def foo[S >: AnyRef](x: T): S = null
         |}
         |
         |class A  {
         |  val d = new D[Int]()
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class D[T] {
         |  def foo[S >: AnyRef](x: T): S = null
         |}
         |
         |class A  {
         |  val d = new D[Int]()
         |
         |  def foo[S >: AnyRef](x: Int): S = d.foo(x)
         |}""".stripMargin
    doTest(text, result)
  }
}
