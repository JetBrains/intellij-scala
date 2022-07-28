package org.jetbrains.plugins.scala
package lang
package overrideImplement

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.testFramework.EditorTestUtil._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.invokeOverrideImplement
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

abstract class ScalaOverrideImplementTestBase extends base.ScalaLightCodeInsightFixtureTestAdapter {

  protected def runTest(methodName: String,
                        fileText: String,
                        expectedText: String,
                        isImplement: Boolean,
                        settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)),
                        copyScalaDoc: Boolean = false,
                        fileName: String = "dummy.scala"): Unit = {
    implicit val project: Project = getProject

    myFixture.configureByText(fileName, convertLineSeparators(fileText))

    val oldSettings = prepareSettings(settings)
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)

    implicit val editor: Editor = getEditor
    ScalaApplicationSettings.getInstance.COPY_SCALADOC = copyScalaDoc
    invokeOverrideImplement(getFile, isImplement, methodName)

    rollbackSettings(oldSettings)
    myFixture.checkResult(convertLineSeparators(expectedText))
  }

  protected def prepareSettings(newSettings: ScalaCodeStyleSettings)(implicit project: Project): ScalaCodeStyleSettings = {
    val oldSettings = ScalaCodeStyleSettings.getInstance(project).clone().asInstanceOf[ScalaCodeStyleSettings]
    TypeAnnotationSettings.set(project, newSettings)
    oldSettings
  }

  protected def rollbackSettings(oldSettings: ScalaCodeStyleSettings)(implicit project: Project): Unit =
    TypeAnnotationSettings.set(project, oldSettings)
}

class ScalaOverrideImplementTest extends ScalaOverrideImplementTestBase {

  def testFoo(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  def foo(x: b): b
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  def foo(x: b): b = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |abstract class b {
         |  def foo(x: b): b
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testEmptyLinePos(): Unit = {
    val fileText =
      s"""
         |package test
         |class Empty extends b {
         |  def foo(): Int = 3
         |
         |
         |  $CARET_TAG
         |
         |
         |}
         |abstract class b {
         |  def too: b
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |class Empty extends b {
         |  def foo(): Int = 3
         |
         |  def too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |abstract class b {
         |  def too: b
         |}
      """.stripMargin
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testNewLineBetweenMethods(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class MethodsNewLine extends b {
         |  def foo(): Int = 3$CARET_TAG
         |}
         |abstract class b {
         |  def too: b
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class MethodsNewLine extends b {
         |  def foo(): Int = 3
         |
         |  def too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |abstract class b {
         |  def too: b
         |}
      """.stripMargin
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testNewLineUpper(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class UpperNewLine extends b {
         |  $CARET_TAG
         |  def foo(): Int = 3
         |}
         |abstract class b {
         |  def too: b
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class UpperNewLine extends b {
         |
         |  def too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |  def foo(): Int = 3
         |}
         |abstract class b {
         |  def too: b
         |}
      """.stripMargin
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideFunction(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class A {
         |  def foo(): A = null
         |}
         |class FunctionOverride extends A {
         |  val t = foo()
         |
         |
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class A {
         |  def foo(): A = null
         |}
         |class FunctionOverride extends A {
         |  val t = foo()
         |
         |  override def foo(): A = ${SELECTION_START_TAG}super.foo()$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testImplementTypeAlias(): Unit = {
    val fileText =
      s"""
         |package Y
         |trait Aa {
         |  type K
         |}
         |class TypeAlias extends Aa {
         |  val t = foo()
         |  $CARET_TAG
         |  def y(): Int = 3
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package Y
         |trait Aa {
         |  type K
         |}
         |class TypeAlias extends Aa {
         |  val t = foo()
         |
         |  type K = ${SELECTION_START_TAG}this.type$SELECTION_END_TAG
         |
         |  def y(): Int = 3
         |}
      """.stripMargin
    val methodName: String = "K"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideValue(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class A {
         |  val foo: A = new A
         |}
         |class OverrideValue extends A {
         |  val t = foo()
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class A {
         |  val foo: A = new A
         |}
         |class OverrideValue extends A {
         |  val t = foo()
         |  override val foo: A = ${SELECTION_START_TAG}_$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testImplementVar(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait A {
         |  var foo: A
         |}
         |class VarImplement extends A {
         |  val t = foo()
         |  $CARET_TAG
         |  def y(): Int = 3
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait A {
         |  var foo: A
         |}
         |class VarImplement extends A {
         |  val t = foo()
         |
         |  var foo: A = ${SELECTION_START_TAG}_$SELECTION_END_TAG
         |
         |  def y(): Int = 3
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testImplementFromSelfType(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait A {
         |  def foo: Int
         |}
         |trait B {
         |  self: A =>
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait A {
         |  def foo: Int
         |}
         |trait B {
         |  self: A =>
         |  def foo: Int = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideFromSelfType(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait A {
         |  def foo: Int = 1
         |}
         |trait B {
         |  self: A =>
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait A {
         |  def foo: Int = 1
         |}
         |trait B {
         |  self: A =>
         |  override def foo = ${SELECTION_START_TAG}self.foo$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
    runTest(methodName, fileText, expectedText, isImplement, settings = TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
  }

  def testTypeAlias(): Unit = {
    val fileText =
      s"""
         |class ImplementTypeAlias extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  type L
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class ImplementTypeAlias extends b {
         |  type L = ${SELECTION_START_TAG}this.type$SELECTION_END_TAG
         |}
         |abstract class b {
         |  type L
         |}
      """.stripMargin
    val methodName: String = "L"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testVal(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Val extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  val too: b
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Val extends b {
         |  val too: b = ${SELECTION_START_TAG}_$SELECTION_END_TAG
         |}
         |abstract class b {
         |  val too: b
         |}
      """.stripMargin
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testVar(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Var extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  var too: b
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Var extends b {
         |  var too: b = ${SELECTION_START_TAG}_$SELECTION_END_TAG
         |}
         |abstract class b {
         |  var too: b
         |}
      """.stripMargin
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testClassTypeParam(): Unit = {
    val fileText =
      s"""
         |class A[T] {
         |  def foo: T = new T
         |}
         |
         |class ClassTypeParam extends A[Int] {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A[T] {
         |  def foo: T = new T
         |}
         |
         |class ClassTypeParam extends A[Int] {
         |  override def foo: Int = ${SELECTION_START_TAG}super.foo$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testHardSubstituting(): Unit = {
    val fileText =
      s"""
         |class A[T] {
         |  def foo(x: (T) => T, y: (T, Int) => T): Double = 1.0
         |}
         |
         |class Substituting extends A[Float] {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A[T] {
         |  def foo(x: (T) => T, y: (T, Int) => T): Double = 1.0
         |}
         |
         |class Substituting extends A[Float] {
         |  override def foo(x: Float => Float, y: (Float, Int) => Float): Double = ${SELECTION_START_TAG}super.foo(x, y)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSimpleTypeParam(): Unit = {
    val fileText =
      s"""
         |abstract class A {
         |  def foo[T](x: T): T
         |}
         |class SimpleTypeParam extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |abstract class A {
         |  def foo[T](x: T): T
         |}
         |class SimpleTypeParam extends A {
         |  def foo[T](x: T): T = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL1997(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo {
         |  def foo(a: Any*): Any
         |}
         |
         |trait Sub extends Foo {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo {
         |  def foo(a: Any*): Any
         |}
         |
         |trait Sub extends Foo {
         |  def foo(a: Any*): Any = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL1999(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Parent {
         |  def m(p: T forSome {type T <: Number})
         |}
         |
         |class Child extends Parent {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Parent {
         |  def m(p: T forSome {type T <: Number})
         |}
         |
         |class Child extends Parent {
         |  def m(p: (T) forSome {type T <: Number}): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "m"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2540(): Unit = {
    val fileText =
      s"""
         |class A {
         |  def foo(x_ : Int) = 1
         |}
         |
         |class B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A {
         |  def foo(x_ : Int) = 1
         |}
         |
         |class B extends A {
         |  override def foo(x_ : Int): Int = ${SELECTION_START_TAG}super.foo(x_)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2010(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Parent {
         |  def doSmth(smth: => String) {}
         |}
         |
         |class Child extends Parent {
         | $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Parent {
         |  def doSmth(smth: => String) {}
         |}
         |
         |class Child extends Parent {
         |  override def doSmth(smth: => String): Unit = ${SELECTION_START_TAG}super.doSmth(smth)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "doSmth"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2052A(): Unit = {
    val fileText =
      s"""
         |class A {
         |  type ID[X] = X
         |  def foo(in: ID[String]): ID[Int] = null
         |}
         |
         |class B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A {
         |  type ID[X] = X
         |  def foo(in: ID[String]): ID[Int] = null
         |}
         |
         |class B extends A {
         |  override def foo(in: ID[String]): ID[Int] = ${SELECTION_START_TAG}super.foo(in)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2052B(): Unit = {
    val fileText =
      s"""
         |class A {
         |  type ID[X] = X
         |  val foo: ID[Int] = null
         |}
         |
         |class B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A {
         |  type ID[X] = X
         |  val foo: ID[Int] = null
         |}
         |
         |class B extends A {
         |  override val foo: ID[Int] = ${SELECTION_START_TAG}_$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2052C(): Unit = {
    val fileText =
      s"""
         |class A {
         |  type F = (Int => String)
         |  def foo(f: F): Any = null
         |}
         |
         |object B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A {
         |  type F = (Int => String)
         |  def foo(f: F): Any = null
         |}
         |
         |object B extends A {
         |  override def foo(f: B.F): Any = ${SELECTION_START_TAG}super.foo(f)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL3808(): Unit = {
    val fileText =
      s"""
         |trait TC[_]
         |
         |class A {
         |  def foo[M[X], N[X[_]]: TC]: String = ""
         |}
         |
         |object B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |trait TC[_]
         |
         |class A {
         |  def foo[M[X], N[X[_]]: TC]: String = ""
         |}
         |
         |object B extends A {
         |  override def foo[M[X], N[X[_]] : TC]: String = ${SELECTION_START_TAG}super.foo$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL3305(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |object A {
         |
         |  object Nested {
         |
         |    class Nested2
         |
         |  }
         |
         |}
         |
         |abstract class B {
         |  def foo(v: A.Nested.Nested2)
         |}
         |
         |class C extends B {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |import test.A.Nested
         |
         |object A {
         |
         |  object Nested {
         |
         |    class Nested2
         |
         |  }
         |
         |}
         |
         |abstract class B {
         |  def foo(v: A.Nested.Nested2)
         |}
         |
         |class C extends B {
         |  def foo(v: Nested.Nested2): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testUnitReturn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  def foo(x: b): Unit
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  def foo(x: b): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |abstract class b {
         |  def foo(x: b): Unit
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testUnitInferredReturn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  def foo(x: b) = ()
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  override def foo(x: b): Unit = ${SELECTION_START_TAG}super.foo(x)$SELECTION_END_TAG
         |}
         |abstract class b {
         |  def foo(x: b) = ()
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testInferredReturn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  $CARET_TAG
         |}
         |abstract class b {
         |  def foo(x: b) = 1
         |}
      """.stripMargin
    val expectedText =
      s"""
         |package test
         |
         |class Foo extends b {
         |  override def foo(x: b): Int = ${SELECTION_START_TAG}super.foo(x)$SELECTION_END_TAG
         |}
         |abstract class b {
         |  def foo(x: b) = 1
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testNoExplicitReturn(): Unit = {
    val fileText =
      s"""
         |class A {
         |  def foo(x : Int): Int = 1
         |}
         |
         |class B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |class A {
         |  def foo(x : Int): Int = 1
         |}
         |
         |class B extends A {
         |  override def foo(x: Int): Int = ${SELECTION_START_TAG}super.foo(x)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))

    runTest(methodName, fileText, expectedText, isImplement, settings)
  }

  def testImplicitParams(): Unit = {
    val fileText =
      s"""
         |trait A {
         |  def foo(x : Int)(implicit name: String): Int = name + x
         |}
         |
         |class B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |trait A {
         |  def foo(x : Int)(implicit name: String): Int = name + x
         |}
         |
         |class B extends A {
         |  override def foo(x: Int)(implicit name: String): Int = ${SELECTION_START_TAG}super.foo(x)$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  //don't add return type for protected
  def testProtectedMethod(): Unit = {
    val fileText =
      s"""
         |abstract class A {
         |  protected def foo(): Unit
         |}
         |
         |class B extends A {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |abstract class A {
         |  protected def foo(): Unit
         |}
         |
         |class B extends A {
         |  protected def foo() = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))

    runTest(methodName, fileText, expectedText, isImplement, settings = TypeAnnotationSettings.noTypeAnnotationForProtected(settings))
  }

  def testProtectedMethodNoBody(): Unit = {
    val fileText =
      s"""
         |abstract class A {
         |  protected def foo(): Unit
         |}
         |
         |class B$CARET_TAG extends A
      """.stripMargin
    val expectedText =
      s"""
         |abstract class A {
         |  protected def foo(): Unit
         |}
         |
         |class B extends A {
         |  protected def foo(): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideProtectedMethodNoBody(): Unit = {
    val fileText =
      s"""
         |abstract class A {
         |  protected def foo(): Unit = {}
         |}
         |
         |class B$CARET_TAG extends A
      """.stripMargin
    val expectedText =
      s"""
         |abstract class A {
         |  protected def foo(): Unit = {}
         |}
         |
         |class B extends A {
         |  override protected def foo(): Unit = ${SELECTION_START_TAG}super.foo()$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }


  def testCopyScalaDoc(): Unit = {
    val fileText =
      s"""
         |abstract class A {
         |
         |  /**
         |   * qwerty
         |   *
         |   * @return
         |   */
         |  protected def foo(): Unit = {}
         |}
         |
         |class B$CARET_TAG extends A
      """.stripMargin
    val expectedText =
      s"""
         |abstract class A {
         |
         |  /**
         |   * qwerty
         |   *
         |   * @return
         |   */
         |  protected def foo(): Unit = {}
         |}
         |
         |class B extends A {
         |  /**
         |   * qwerty
         |   *
         |   * @return
         |   */
         |  override protected def foo(): Unit = ${SELECTION_START_TAG}super.foo()$SELECTION_END_TAG
         |}
      """.stripMargin
    val methodName: String = "foo"
    val isImplement = false
    val copyScalaDoc = true
    runTest(methodName, fileText, expectedText, isImplement, copyScalaDoc = copyScalaDoc)
  }

  def testNoImportScalaSeq(): Unit = {
    val fileText =
      s"""
         |import scala.collection.Seq
         |
         |class Test {
         |  def foo: Seq[Int] = Seq(1)
         |}
         |
         |class Test2 extends Test {
         |$CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |import scala.collection.Seq
         |
         |class Test {
         |  def foo: Seq[Int] = Seq(1)
         |}
         |
         |class Test2 extends Test {
         |  override def foo: Seq[Int] = super.foo
         |}
      """.stripMargin

    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideClassParam(): Unit = {
    val fileText =
      s"""
         |class Parent(val param1: Int, var param2: String)
         |
         |class Child extends Parent(4, "") {
         |  $CARET_TAG
         |}
      """.stripMargin

    val expectedText =
      s"""
         |class Parent(val param1: Int, var param2: String)
         |
         |class Child extends Parent(4, "") {
         |  override val param1: Int = _
         |}
      """.stripMargin

    runTest("param1", fileText, expectedText, isImplement = false)
  }

  def testDoNotSaveAnnotations(): Unit = {
    val fileText =
      s"""
         |trait Base {
         |  @throws(classOf[Exception])
         |  @deprecated
         |  def annotFoo(int: Int): Int = 45
         |}
         |
         |class Inheritor extends Base {
         | $CARET_TAG
         |}
      """.stripMargin

    val expectedText =
      s"""
         |trait Base {
         |  @throws(classOf[Exception])
         |  @deprecated
         |  def annotFoo(int: Int): Int = 45
         |}
         |
         |class Inheritor extends Base {
         |  override def annotFoo(int: Int): Int = super.annotFoo(int)
         |}
      """.stripMargin

    val methodName: String = "annotFoo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testInfixTypeAlias(): Unit = {
    val fileText =
      s"""
         |import tag._
         |
         |object tag {
         |  trait Tagged[U]
         |  type @@[+T, U] = T with Tagged[U]
         |}
         |
         |
         |trait UserRepo {
         |  sealed trait UserId // used for tagging
         |
         |  def deleteUser(userId: Int @@ UserId)
         |}
         |
         |class UserRepoImpl extends UserRepo {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expectedText =
      s"""
         |import tag._
         |
         |object tag {
         |  trait Tagged[U]
         |  type @@[+T, U] = T with Tagged[U]
         |}
         |
         |
         |trait UserRepo {
         |  sealed trait UserId // used for tagging
         |
         |  def deleteUser(userId: Int @@ UserId)
         |}
         |
         |class UserRepoImpl extends UserRepo {
         |  def deleteUser(userId: Int @@ UserId): Unit = ???
         |}
      """.stripMargin
    val methodName = "deleteUser"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testAbstractMethodModifier(): Unit = {
    val fileText =
      s"""
         |abstract class ClassToOverride {
         |  abstract def methodToOverride(): Unit
         |}
         |
         |class OverridingClass extends ClassToOverride {
         |  $CARET_TAG
         |}""".stripMargin
    val expectedResult =
      s"""
         |abstract class ClassToOverride {
         |  abstract def methodToOverride(): Unit
         |}
         |
         |class OverridingClass extends ClassToOverride {
         |  def methodToOverride(): Unit = ???
         |}""".stripMargin
    val methodName = "methodToOverride"
    runTest(methodName, fileText, expectedResult, isImplement = true)
  }

  def testImplementKindProjectorLambdaInline(): Unit = {
    val text =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[Either[String, ?]] {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expected =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[Either[String, ?]] {
         |  def monad: Monad[Either[String, ?]] = ???
         |}
      """.stripMargin
    val methodName = "monad"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testImplementKindProjectorLambdaFunctionSyntax(): Unit = {
    val text =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[Lambda[A => (A, A)]] {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expected =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[Lambda[A => (A, A)]] {
         |  def monad: Monad[Lambda[A => (A, A)]] = ???
         |}
      """.stripMargin
    val methodName = "monad"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testImplementKindProjectorLambdaWithVariance(): Unit = {
    val text =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[Lambda[`+A` => Either[List[A], List[A]]]] {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expected =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[Lambda[`+A` => Either[List[A], List[A]]]] {
         |  def monad: Monad[Lambda[`+A` => Either[List[A], List[A]]]] = ???
         |}
      """.stripMargin
    val methodName = "monad"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testImplementKindProjectorAppliedLambda(): Unit = {
    val text =
      s"""
         |trait Foo[F[_, _]] {
         |  def foo: F[Double, String]
         |}
         |
         |class Bar extends Foo[λ[(-[A], +[B]) => (A, Int) => B]] {
         |  $CARET_TAG
         |}
      """.stripMargin
    val expected =
      s"""
         |trait Foo[F[_, _]] {
         |  def foo: F[Double, String]
         |}
         |
         |class Bar extends Foo[λ[(-[A], +[B]) => (A, Int) => B]] {
         |  def foo: (Double, Int) => String = ???
         |}
      """.stripMargin
    val methodName = "foo"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testImplementPlainTypeLambda(): Unit = {
    val text =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[({ type L[A] = List[(String, A)] })#L] {
         | $CARET_TAG
         |}
      """.stripMargin
    val expected =
      s"""
         |trait Monad[F[_]]
         |trait Foo[F[_]] {
         |  def monad: Monad[F]
         |}
         |
         |class Bar extends Foo[({ type L[A] = List[(String, A)] })#L] {
         |  def monad: Monad[Lambda[A => List[(String, A)]]] = ???
         |}
      """.stripMargin
    val methodName = "monad"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testImplementTypeLambdaInInfixType(): Unit = {
    val text =
      s"""
         |trait ~>[F[_], G[_]]
         |trait Monad[F[_]]
         |type ReaderT[F[_], A, B] = F
         |
         |trait Unlift[F[_], G[_]] {
         |  self =>
         |  def unlift: G[G ~> F]
         |}
         |
         |implicit def reader[F[_]: Monad, R]: Unlift[F, ReaderT[F, R, ?]] =
         |    new Unlift[F, ReaderT[F, R, ?]] {
         |      $CARET_TAG
         |    }
      """.stripMargin
    val expected =
      """
        |trait ~>[F[_], G[_]]
        |trait Monad[F[_]]
        |type ReaderT[F[_], A, B] = F
        |
        |trait Unlift[F[_], G[_]] {
        |  self =>
        |  def unlift: G[G ~> F]
        |}
        |
        |implicit def reader[F[_]: Monad, R]: Unlift[F, ReaderT[F, R, ?]] =
        |    new Unlift[F, ReaderT[F, R, ?]] {
        |      def unlift: ReaderT[F, R, ReaderT[F, R, ?] ~> F] = ???
        |    }
      """.stripMargin
    val methodName = "unlift"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testImplementScopedVisibilityModifier(): Unit = {
    val text =
      s"""
         |object Foo {
         |  sealed trait Bar {
         |    private[Foo] def foo: Unit
         |  }
         |
         |  class Baz extends Bar {
         |    $CARET_TAG
         |  }
         |}
      """.stripMargin
    val expected =
      s"""
         |object Foo {
         |  sealed trait Bar {
         |    private[Foo] def foo: Unit
         |  }
         |
         |  class Baz extends Bar {
         |    private[Foo] def foo: Unit = ???
         |  }
         |}
      """.stripMargin
    val methodName = "foo"
    runTest(methodName, text, expected, isImplement = true)
  }

  def testWorksheet(): Unit = runTest(
    "length",
    fileText =
      s"""sealed trait List {
         |  def length: Int
         |}
         |
         |final case class Cons(head: Int, tail: List) extends List {
         |  $CARET_TAG
         |}
         |
         |case object Nil extends List
         |""".stripMargin,
    expectedText =
      s"""sealed trait List {
         |  def length: Int
         |}
         |
         |final case class Cons(head: Int, tail: List) extends List {
         |  def length: Int = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |
         |case object Nil extends List
         |""".stripMargin,
    isImplement = true,
    fileName = "foo.sc"
  )
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaOverrideImplementTest_3_Latest extends ScalaOverrideImplementTestBase {
  override protected def prepareSettings(newSettings: ScalaCodeStyleSettings)(implicit project: Project) = {
    val oldSettings = super.prepareSettings(newSettings)
    ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX = newSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    oldSettings
  }

  override protected def rollbackSettings(oldSettings: ScalaCodeStyleSettings)(implicit project: Project): Unit = {
    ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX = oldSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    super.rollbackSettings(oldSettings)
  }

  private def defaultSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))

  private def settingsWithIndentationBasedSyntax = {
    val settings = defaultSettings
    settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
    settings
  }

  private def settingsWithoutIndentationBasedSyntax = {
    val settings = defaultSettings
    settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false
    settings
  }

  def testImplementUsingIndentationBasedSyntaxWhenSettingIsOn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_19753_ImplementUsingIndentationBasedSyntaxWhenSettingIsOn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo:
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_19753_ImplementUsingIndentationBasedSyntaxWhenSettingIsOff(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo:
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  def testImplementUsingStandardSyntaxWithBracesWhenSettingIsOff(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo {
         |  def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }
}
