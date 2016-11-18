package org.jetbrains.plugins.scala
package codeInsight.delegateMethod

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.codeInsight.delegate.ScalaGenerateDelegateHandler
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
 * Nikolay.Tropin
 * 2014-03-26
 */
class ScalaDelegateMethodTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def runTest(fileText: String, expectedText: String,
              defaultSettings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))) {
    configureFromFileTextAdapter("dummy.scala", fileText.replace("\r", "").stripMargin.trim)
    val oldSettings = ScalaCodeStyleSettings.getInstance(getProjectAdapter).clone().asInstanceOf[ScalaCodeStyleSettings]
    TypeAnnotationSettings.set(getProjectAdapter, defaultSettings)
    new ScalaGenerateDelegateHandler().invoke(getProjectAdapter, getEditorAdapter, getFileAdapter)
    TypeAnnotationSettings.set(getProjectAdapter, oldSettings)
    checkResultByText(expectedText.replace("\r", "").stripMargin.trim)
  }

  def testVal() {
    val text =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  val d = new D
        |  <caret>
        |}"""
    val resultText =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  val d = new D
        |
        |  def foo(x: Int): Int = d.foo(x)
        |}"""
    runTest(text, resultText)
  }

  def testVar() {
    val text =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  var d = new D
        |  <caret>
        |}"""
    val resultText =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  var d = new D
        |
        |  def foo(x: Int): Int = d.foo(x)
        |}"""
    runTest(text, resultText)
  }

  def testDefParameterless() {
    val text =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  def d = new D
        |  <caret>
        |}"""
    val resultText =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  def d = new D
        |
        |  def foo(x: Int): Int = d.foo(x)
        |}"""
    runTest(text, resultText)
  }

  def testDefEmptyParen() {
    val text =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  def d() = new D
        |  <caret>
        |}"""
    val resultText =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A {
        |  def d() = new D
        |
        |  def foo(x: Int): Int = d().foo(x)
        |}"""
    runTest(text, resultText)
  }

  def testDelegateCompoundType() {
    val text =
      """trait DT {
        |  def foo(x: Int): Int = x
        |}
        |
        |class DC {}
        |
        |class A {
        |  val d = new DC with DT
        |  <caret>
        |}"""
    val resultText =
      """trait DT {
        |  def foo(x: Int): Int = x
        |}
        |
        |class DC {}
        |
        |class A {
        |  val d = new DC with DT
        |
        |  def foo(x: Int): Int = d.foo(x)
        |}"""
    runTest(text, resultText)
  }

  def testTargetFromBaseClass() {
    val text =
      """class Base {
        |  val d = new D()
        |}
        |
        |class D {
        |  def foo() {}
        |}
        |
        |class A extends Base {
        |  <caret>
        |}"""
    val result =
      """class Base {
        |  val d = new D()
        |}
        |
        |class D {
        |  def foo() {}
        |}
        |
        |class A extends Base {
        |  def foo(): Unit = d.foo()
        |}"""
    runTest(text, result)
  }

  def testPrivateFromBaseClass() {
    val text =
      """class Base {
        |  private val d = new D()
        |}
        |
        |class D {
        |  def foo() {}
        |}
        |
        |class A extends Base {
        |<caret>
        |}"""
    val result = //no action
      """class Base {
        |  private val d = new D()
        |}
        |
        |class D {
        |  def foo() {}
        |}
        |
        |class A extends Base {
        |
        |}"""
    runTest(text, result)
  }

  def testOverride() {
    val text =
      """trait DT {
        |  def foo(x: Int): Int = x
        |}
        |
        |class DC extends DT
        |
        |class A extends DT {
        |  val d = new DC
        |  <caret>
        |}"""
    val result =
      """trait DT {
        |  def foo(x: Int): Int = x
        |}
        |
        |class DC extends DT
        |
        |class A extends DT {
        |  val d = new DC
        |
        |  override def foo(x: Int): Int = d.foo(x)
        |}"""
    runTest(text, result)
  }

  def testInInner() {
    val text =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A  {
        |  val d = new D
        |  val inner = new AnyRef {
        |    <caret>
        |  }
        |}"""
    val result =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A  {
        |  val d = new D
        |  val inner = new AnyRef {
        |    def foo(x: Int): Int = d.foo(x)
        |  }
        |}"""
    runTest(text, result)
  }

  def testInInner2() = {
    val text =
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A  {
        |  val inner = new AnyRef {
        |<caret>
        |  }
        |}"""
    val result = //no effect
      """class D {
        |  def foo(x: Int): Int = x
        |}
        |
        |class A  {
        |  val inner = new AnyRef {
        |
        |  }
        |}"""
    runTest(text, result)
  }

  def testMultipleParamList() {
    val text =
      """class D {
        |  def foo(x: Int)(y: Int): Int = x
        |}
        |
        |class A  {
        |  val d = new D()
        |  <caret>
        |}"""
    val result =
      """class D {
        |  def foo(x: Int)(y: Int): Int = x
        |}
        |
        |class A  {
        |  val d = new D()
        |
        |  def foo(x: Int)(y: Int): Int = d.foo(x)(y)
        |}"""
    runTest(text, result)
  }

  def testGenericDelegate() {
    val text =
      """class D[T] {
        |  def foo(x: T): T = x
        |}
        |
        |class A  {
        |  val d = new D[Int]()
        |  <caret>
        |}"""
    val result =
      """class D[T] {
        |  def foo(x: T): T = x
        |}
        |
        |class A  {
        |  val d = new D[Int]()
        |
        |  def foo(x: Int): Int = d.foo(x)
        |}"""
    runTest(text, result)
  }

  def testMethodCallNeedTypeParam() {
    val text =
      """class D[T] {
        |  def foo[S <: T](x: T): T = x
        |}
        |
        |class A  {
        |  val d = new D[AnyRef]()
        |  <caret>
        |}"""
    val result =
      """class D[T] {
        |  def foo[S <: T](x: T): T = x
        |}
        |
        |class A  {
        |  val d = new D[AnyRef]()
        |
        |  def foo[S <: AnyRef](x: AnyRef): AnyRef = d.foo[S](x)
        |}"""
    runTest(text, result)
  }

  def testNeedTypeParamWithoutRetType() {
    val text =
      """class D[T] {
        |  def foo[S >: AnyRef](x: T): S = null
        |}
        |
        |class A  {
        |  val d = new D[Int]()
        |  <caret>
        |}"""
    val result =
      """class D[T] {
        |  def foo[S >: AnyRef](x: T): S = null
        |}
        |
        |class A  {
        |  val d = new D[Int]()
        |
        |  def foo[S >: AnyRef](x: Int) = d.foo[S](x)
        |}"""

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))
    runTest(text, result, defaultSettings = TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
  }

  def testNoTypeParamWithReturn() {
    val text =
      """class D[T] {
        |  def foo[S >: AnyRef](x: T): S = null
        |}
        |
        |class A  {
        |  val d = new D[Int]()
        |  <caret>
        |}"""
    val result =
      """class D[T] {
        |  def foo[S >: AnyRef](x: T): S = null
        |}
        |
        |class A  {
        |  val d = new D[Int]()
        |
        |  def foo[S >: AnyRef](x: Int): S = d.foo(x)
        |}"""
    runTest(text, result)
  }
}
