package org.jetbrains.plugins.scala.lang.overrideImplement

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.testFramework.EditorTestUtil._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.invokeOverrideImplement
import org.jetbrains.plugins.scala.overrideImplement.{ClassMember, ScExtensionMethodMember, ScMethodMember, ScalaOIUtil}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

import scala.reflect.ClassTag

abstract class ScalaOverrideImplementTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected def runTest(
    methodName: String,
    fileText: String,
    expectedText: String,
    isImplement: Boolean,
    settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)),
    copyScalaDoc: Boolean = false,
    fileName: String = "dummy.scala"
  ): Unit = runTest(
    Some(methodName),
    fileText,
    expectedText,
    isImplement,
    settings,
    copyScalaDoc,
    fileName
  )

  protected def runImplementAllTest(
    fileText: String,
    expectedText: String,
    copyScalaDoc: Boolean = false
  ): Unit = {
    val isImplement = true
    val fileName: String = "dummy.scala"
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
    runTest(
      None,
      fileText,
      expectedText,
      isImplement,
      settings,
      copyScalaDoc,
      fileName
    )
  }

  protected def runTest(
    methodName: Option[String],
    fileText: String,
    expectedText: String,
    isImplement: Boolean,
    settings: ScalaCodeStyleSettings,
    copyScalaDoc: Boolean,
    fileName: String
  ): Unit = {
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

  protected def assertTemplateDefinitionSelectedForAction(
    fileText: String,
    expectedName: String
  ): Unit = {
    val testName = getTestName(false) + ".scala"
    myFixture.configureByText(testName, fileText.withNormalizedSeparator)

    val actualDef = ScalaOIUtil.getTemplateDefinitionForOverrideImplementAction(getFile, getEditor)
    val actualDefName = actualDef.map(_.name).orNull
    assertEquals("Wrong definition was selected for override/implement action", expectedName, actualDefName)
  }

  protected def assertMembersPresentableText[T <: ClassMember : ClassTag](
    fileText: String,
    className: String,
    filterMethods: T => Boolean,
    expectedMembersConcatenatedText: String
  ): Unit = {
    configureFromFileText(fileText.withNormalizedSeparator)

    val clazz: ScTemplateDefinition = getFile.asInstanceOf[ScalaFile]
      .elements.collectFirst { case c: ScTemplateDefinition if c.name == className => c }
      .getOrElse(throw new AssertionError(s"Can't find definition with name $className"))

    val members = ScalaOIUtil.getAllMembersToOverride(clazz)
    val membersFilteredAndSorted = members
      .filterByType[T].filter(filterMethods)
      .sortBy(_.getElement.getNode.getStartOffset)

    assertEquals(
      expectedMembersConcatenatedText.trim,
      membersFilteredAndSorted.map(_.getText).mkString("\n")
    )
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
         |  override def foo(x: b): b = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |
         |  override def too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override type K = ${SELECTION_START_TAG}this.type$SELECTION_END_TAG
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
         |  override val foo: A = ${SELECTION_START_TAG}???$SELECTION_END_TAG
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
         |  override var foo: A = ${SELECTION_START_TAG}???$SELECTION_END_TAG
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
         |  override def foo: Int = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override type L = ${SELECTION_START_TAG}this.type$SELECTION_END_TAG
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
         |  override val too: b = ${SELECTION_START_TAG}???$SELECTION_END_TAG
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
         |  override var too: b = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def foo[T](x: T): T = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def foo(a: Any*): Any = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def m(p: (T) forSome {type T <: Number}): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override val foo: ID[Int] = ${SELECTION_START_TAG}???$SELECTION_END_TAG
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
         |  object Nested {
         |    class Nested2
         |  }
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
         |  object Nested {
         |    class Nested2
         |  }
         |}
         |
         |abstract class B {
         |  def foo(v: A.Nested.Nested2)
         |}
         |
         |class C extends B {
         |  override def foo(v: Nested.Nested2): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def foo(x: b): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override protected def foo() = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override protected def foo(): Unit = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override val param1: Int = ???
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
         |  override def deleteUser(userId: Int @@ UserId): Unit = ???
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
         |  override def methodToOverride(): Unit = ???
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
         |  override def monad: Monad[Either[String, ?]] = ???
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
         |  override def monad: Monad[Lambda[A => (A, A)]] = ???
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
         |  override def monad: Monad[Lambda[`+A` => Either[List[A], List[A]]]] = ???
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
         |  override def foo: (Double, Int) => String = ???
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
         |  override def monad: Monad[Lambda[A => List[(String, A)]]] = ???
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
        |      override def unlift: ReaderT[F, R, ReaderT[F, R, ?] ~> F] = ???
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
         |    override private[Foo] def foo: Unit = ???
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
         |  override def length: Int = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |
         |case object Nil extends List
         |""".stripMargin,
    isImplement = true,
    fileName = "foo.sc"
  )

  def testMethodMemberPresentableText(): Unit = {
    val fileText =
      """class MyClass {
        |  def myFunction1: String = ???
        |  def myFunction2(param1: String, param2: String)(implicit context: Double): String = ???
        |  def myFunction3[T <: CharSequence, E](param1: String): String = ???
        |}
        |""".stripMargin

    assertMembersPresentableText[ScMethodMember](
      fileText,
      "MyClass",
      _.name.startsWith("my"),
      """myFunction1: String
        |myFunction2(param1: String, param2: String)(context: Double): String
        |myFunction3[T <: CharSequence, E](param1: String): String
        |""".stripMargin,
    )
  }

  def testImplementWhenCaretIsAtTheEndOfFile(): Unit = {
    runImplementAllTest(
      s"""class A extends Function[Int, String]$CARET""",
      s"""class A extends Function[Int, String] {
         |  override def apply(v1: Int): String = ???
         |}""".stripMargin
    )
  }
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

  private def settingsWithIndentationBasedSyntax: ScalaCodeStyleSettings = {
    val settings = defaultSettings
    settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
    settings
  }

  private def settingsWithoutIndentationBasedSyntax: ScalaCodeStyleSettings = {
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
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
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
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsideAnEmptyTemplateBody(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsideAnEmptyTemplateBody_CaretUnindented_ButStillSomeCodeIsExpectedInTemplateBody(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |$CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheBeginningOfTemplateBody(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheMiddleOfTemplateBody(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |  $CARET_TAG
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheEndOfTemplateBody(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheEndOfTemplateBody_WithEndMarker(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |  $CARET_TAG
         |end Bar
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |end Bar
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level2_1(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |        $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level2_2(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |      $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level1_1(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |     $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper1")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level1_2(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |    $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper1")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level0_1(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |   $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper0")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level0_2(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |  $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper0")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level_NotChosen(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |$CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, null)
  }

  def testImplementUsingBracesSyntax_IndentationShouldNotMatter_StartOfTheBlock(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2 {
         |    $CARET
         |      println()
         |      println()
         |    }
         |""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingBracesSyntax_IndentationShouldNotMatter_MiddleOfTheBlock(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2 {
         |      println()
         |    $CARET
         |      println()
         |    }
         |""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingBracesSyntax_IndentationShouldNotMatter_EndOfTheBlock(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2 {
         |      println()
         |      println()
         |    $CARET
         |    }
         |""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
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
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo {
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  def testImplementWithUsingParameters(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Context
         |trait Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Context
         |trait Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testOverrideWithUsingParameters(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Context
         |class Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = summon[String]
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Context
         |class Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = summon[String]
         |
         |class Bar extends Foo:
         |  override def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = ${SELECTION_START_TAG}super.foo(x)(y)$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_20350_ImplementUsingIndentationBasedSyntaxInsideAnEmptyGivenTemplateBody_Inner(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |object Container {
         |  given Foo w${CARET_TAG}ith
         |}
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |object Container {
         |  given Foo with
         |    override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_20350_ImplementUsingIndentationBasedSyntaxInsideAnEmptyGivenTemplateBody_TopLevel(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo w${CARET_TAG}ith
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo with
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_20350_ImplementUsingStandardSyntaxInsideAnEmptyGivenTemplateBody_TopLevel(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo w${CARET_TAG}ith
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo with {
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  private def addHelperClassesForExtensionTests(): Unit = {
    getFixture.addFileToProject(
      "helpers.scala",
      """trait MyTrait
        |class MyClass extends MyTrait
        |case class MyCaseClass[T]()
        |class MyContext""".stripMargin
    )
  }

  def testExtension_SimpleSingle_Override(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract: String = ???
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract: String = ???
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExtSimpleSingleNonAbstract: String = ${START}super.myExtSimpleSingleNonAbstract(target)$END
         |""".stripMargin
    runTest("myExtSimpleSingleNonAbstract", before, after, isImplement = false)
  }

  def testExtension_SimpleSingleWithParams_Override(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract(param: Int): String = ???
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract(param: Int): String = ???
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExtSimpleSingleNonAbstract(param: Int): String = ${START}super.myExtSimpleSingleNonAbstract(target)(param)$END
         |""".stripMargin
    runTest("myExtSimpleSingleNonAbstract", before, after, isImplement = false)
  }

  def testExtension_SimpleSingle(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleAbstract: String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleAbstract: String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExtSimpleSingleAbstract: String = ${START}???$END
         |""".stripMargin
    runTest("myExtSimpleSingleAbstract", before, after, isImplement = true)
  }

  def testExtension_MultipleMethods(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExt21: String = ???
         |    override def myExt22(p: Int): String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }

  def testExtension_MultipleMethods_UseBraces(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class MyChildClass extends MyBaseClass {
         |  extension (target: String) {
         |    override def myExt21: String = ???
         |    override def myExt22(p: Int): String = ???
         |  }
         |}
         |""".stripMargin

    ScalaCodeStyleSettings.getInstance(getProject).USE_SCALA3_INDENTATION_BASED_SYNTAX = false
    runImplementAllTest(before, after)
  }

  def testExtension_WithTypeParameters(): Unit = {
    val before =
      s"""abstract class MyBaseClass[T1, T2 <: CharSequence]:
         |  extension [E1](t: E1)
         |    def myExt1[F1, F2 <: T1, F3 <: T2]: String
         |
         |  extension [E1 <: T1](t: E1)
         |    def myExt2: String
         |
         |  extension [E1 <: T2](t: E1)
         |    def myExt3: String
         |
         |class ${CARET}MyChildClass[ChildClassTypeParam1] extends MyBaseClass[ChildClassTypeParam1, String]
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass[T1, T2 <: CharSequence]:
         |  extension [E1](t: E1)
         |    def myExt1[F1, F2 <: T1, F3 <: T2]: String
         |
         |  extension [E1 <: T1](t: E1)
         |    def myExt2: String
         |
         |  extension [E1 <: T2](t: E1)
         |    def myExt3: String
         |
         |class MyChildClass[ChildClassTypeParam1] extends MyBaseClass[ChildClassTypeParam1, String]:
         |  extension [E1](t: E1)
         |    override def myExt1[F1, F2 <: ChildClassTypeParam1, F3 <: String]: String = ???
         |  extension [E1 <: ChildClassTypeParam1](t: E1)
         |    override def myExt2: String = ???
         |  extension [E1 <: String](t: E1)
         |    override def myExt3: String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }


  def testExtension_WithModifier(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt41: String
         |    protected def myExt42(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt41: String
         |    protected def myExt42(p: Int): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExt41: String = ???
         |    override protected def myExt42(p: Int): String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }

  def testExtension_WithSoftModifiers(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    inline def myExt51: String
         |    transparent inline def myExt52(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    inline def myExt51: String
         |    transparent inline def myExt52(p: Int): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override inline def myExt51: String = ???
         |    override transparent inline def myExt52(p: Int): String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }


  def testExtension_CopyDocs(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    /** Doc for myExt1 */
         |    def myExt61: String
         |    /** Doc for myExt2 */
         |    def myExt62: String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    /** Doc for myExt1 */
         |    def myExt61: String
         |    /** Doc for myExt2 */
         |    def myExt62: String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    /** Doc for myExt1 */
         |    override def myExt61: String = ???
         |    /** Doc for myExt2 */
         |    override def myExt62: String = ???
         |""".stripMargin

    runImplementAllTest(before, after, copyScalaDoc = true)
  }

  def testExtension_WithUsingClauses(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: Int)(using MyContext, Long)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt7(p1: Int)(using u1: Short, u2: MyTrait)(p2: Long): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: Int)(using MyContext, Long)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt7(p1: Int)(using u1: Short, u2: MyTrait)(p2: Long): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: Int)(using MyContext, Long)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExt7(p1: Int)(using u1: Short, u2: MyTrait)(p2: Long): String = ???
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runImplementAllTest(before, after)
  }

  def testExtension_WithUsingClausesInTheBeginningOfParametersClause(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (using MyContext)(using Long)(target: Int)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt8(using u1: Short, u2: MyTrait)(p1: Int)(p2: Long): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (using MyContext)(using Long)(target: Int)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt8(using u1: Short, u2: MyTrait)(p1: Int)(p2: Long): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (using MyContext)(using Long)(target: Int)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExt8(using u1: Short, u2: MyTrait)(p1: Int)(p2: Long): String = ???
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runImplementAllTest(before, after)
  }

  def testExtension_CopyTargetNameAnnotation(): Unit = {
    val before =
      s"""import scala.annotation.targetName
         |
         |abstract class MyBaseClass:
         |  extension (target: String)
         |    @targetName("myExtTargetNameTest1_Renamed")
         |    def myExtTargetNameTest1: String
         |    @targetName("myExtTargetNameTest2_Renamed")
         |    def myExtTargetNameTest2: String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""import scala.annotation.targetName
         |
         |abstract class MyBaseClass:
         |  extension (target: String)
         |    @targetName("myExtTargetNameTest1_Renamed")
         |    def myExtTargetNameTest1: String
         |    @targetName("myExtTargetNameTest2_Renamed")
         |    def myExtTargetNameTest2: String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    @targetName("myExtTargetNameTest1_Renamed")
         |    override def myExtTargetNameTest1: String = ???
         |    @targetName("myExtTargetNameTest2_Renamed")
         |    override def myExtTargetNameTest2: String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }

  def testExtension_ComplicatedMixedExample(): Unit = {
    val before =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass[MyClass]
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String
         |
         |class MyChildClass extends MyBaseClass[MyClass]:
         |  extension [T <: MyClass, T2 <: MyTrait](using MyClass, Long)(target: MyClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExtComplex[E <: MyClass, E2 <: MyTrait](a: MyClass)(using b: MyClass, e: E)(t: T): String = ???
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runImplementAllTest(before, after)
  }

  def testExtension_ComplicatedMixedExample_Override(): Unit = {
    val before =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String = ???
         |
         |class ${CARET}MyChildClass extends MyBaseClass[MyClass]
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String = ???
         |
         |class MyChildClass extends MyBaseClass[MyClass]:
         |  extension [T <: MyClass, T2 <: MyTrait](using MyClass, Long)(target: MyClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExtComplex[E <: MyClass, E2 <: MyTrait](a: MyClass)(using b: MyClass, e: E)(t: T): String = super.myExtComplex(target)(a)(t)
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runTest("myExtComplex", before, after, isImplement = false)
  }

  def testExtensionImplementedInNestedIndentationBasedSyntax(): Unit = {
    runImplementAllTest(
      s"""abstract class MyBaseFromScala3:
         |  extension (target: String)
         |    def myExt1(p: String): String
         |    def myExt2(p: String): String
         |  extension (target: String)
         |    def myExt3(p: String): String
         |    def myExt4(p: String): String
         |
         |object wrapper1:
         |  object wrapper2:
         |    object wrapper3:
         |      class ${CARET}MyChildInScala3 extends MyBaseFromScala3
         |""".stripMargin,
      """abstract class MyBaseFromScala3:
        |  extension (target: String)
        |    def myExt1(p: String): String
        |    def myExt2(p: String): String
        |  extension (target: String)
        |    def myExt3(p: String): String
        |    def myExt4(p: String): String
        |
        |object wrapper1:
        |  object wrapper2:
        |    object wrapper3:
        |      class MyChildInScala3 extends MyBaseFromScala3:
        |        extension (target: String)
        |          override def myExt1(p: String): String = ???
        |          override def myExt2(p: String): String = ???
        |        extension (target: String)
        |          override def myExt3(p: String): String = ???
        |          override def myExt4(p: String): String = ???
        |""".stripMargin
    )
  }

  def testExtensionMethodMemberPresentableText(): Unit = {
    val fileText =
      """class MyClass:
        |  extension (target: String)
        |    def myExt1: String = ???
        |""".stripMargin

    assertMembersPresentableText[ScExtensionMethodMember](
      fileText,
      "MyClass",
      _ => true,
      """(target: String) myExt1: String""",
    )
  }

  def testExtensionMethodMemberPresentableText_TypeParametersInExtensionMember(): Unit = {
    val fileText =
      """class MyClass:
        |  extension [T <: CharSequence, E](target: T)(using c: E)
        |    def myExt1[X <: CharSequence, Y]: String = ???
        |""".stripMargin

    assertMembersPresentableText[ScExtensionMethodMember](
      fileText,
      "MyClass",
      _ => true,
      """[T <: CharSequence, E](target: T)(c: E) myExt1[X <: CharSequence, Y]: String""",
    )
  }

  def testExtensionMethodMemberPresentableText_UsingParametersInExtensionMember(): Unit = {
    val fileText =
      """class MyClass:
        |  extension (using context1: Int)(target: String)(using context2: Long)
        |    def myExt2(using context3: Float)(param1: String, param2: String)(using context4: Double): String = ???
        |""".stripMargin

    assertMembersPresentableText[ScExtensionMethodMember](
      fileText,
      "MyClass",
      _ => true,
      """(using context1: Int)(target: String)(using context2: Long) myExt2(context3: Float)(param1: String, param2: String)(context4: Double): String""",
    )
  }
}
