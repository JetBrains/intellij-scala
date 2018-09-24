package org.jetbrains.plugins.scala.lang.overrideImplement

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
 * @author Alefas
 * @since 14.05.12
 */
class ScalaOverrideImplementTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def runTest(methodName: String, fileText: String, expectedText: String, isImplement: Boolean,
              settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)),
              copyScalaDoc: Boolean = false) {
    configureFromFileTextAdapter("dummy.scala", fileText.replace("\r", "").stripMargin.trim)
    val oldSettings = ScalaCodeStyleSettings.getInstance(getProjectAdapter).clone()
    TypeAnnotationSettings.set(getProjectAdapter, settings)
    

    ScalaApplicationSettings.getInstance().COPY_SCALADOC = copyScalaDoc
    ScalaOIUtil.invokeOverrideImplement(getProjectAdapter, getEditorAdapter, getFileAdapter, isImplement, methodName)
    
    TypeAnnotationSettings.set(getProjectAdapter, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    checkResultByText(expectedText.replace("\r", "").stripMargin.trim)
  }

  def testFoo() {
    val fileText =
      """
        |package test
        |
        |class Foo extends b {
        |  <caret>
        |}
        |abstract class b {
        |  def foo(x: b): b
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Foo extends b {
        |  def foo(x: b): b = <selection>???</selection>
        |}
        |abstract class b {
        |  def foo(x: b): b
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testEmptyLinePos() {
    val fileText =
      """
        |package test
        |class Empty extends b {
        |  def foo(): Int = 3
        |
        |
        |  <caret>
        |
        |
        |}
        |abstract class b {
        |  def too: b
        |}
      """
    val expectedText =
      """
        |package test
        |class Empty extends b {
        |  def foo(): Int = 3
        |
        |  def too: b = <selection>???</selection>
        |}
        |abstract class b {
        |  def too: b
        |}
      """
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testNewLineBetweenMethods() {
    val fileText =
      """
        |package test
        |
        |class MethodsNewLine extends b {
        |  def foo(): Int = 3<caret>
        |}
        |abstract class b {
        |  def too: b
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class MethodsNewLine extends b {
        |  def foo(): Int = 3
        |
        |  def too: b = <selection>???</selection>
        |}
        |abstract class b {
        |  def too: b
        |}
      """
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testNewLineUpper() {
    val fileText =
      """
        |package test
        |
        |class UpperNewLine extends b {
        |  <caret>
        |  def foo(): Int = 3
        |}
        |abstract class b {
        |  def too: b
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class UpperNewLine extends b {
        |
        |  def too: b = <selection>???</selection>
        |
        |  def foo(): Int = 3
        |}
        |abstract class b {
        |  def too: b
        |}
      """
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideFunction() {
    val fileText =
      """
        |package test
        |
        |class A {
        |  def foo(): A = null
        |}
        |class FunctionOverride extends A {
        |  val t = foo()
        |
        |
        |  <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class A {
        |  def foo(): A = null
        |}
        |class FunctionOverride extends A {
        |  val t = foo()
        |
        |  override def foo(): A = <selection>super.foo()</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testImplementTypeAlias() {
    val fileText =
      """
        |package Y
        |trait Aa {
        |  type K
        |}
        |class TypeAlias extends Aa {
        |  val t = foo()
        |  <caret>
        |  def y(): Int = 3
        |}
      """
    val expectedText =
      """
        |package Y
        |trait Aa {
        |  type K
        |}
        |class TypeAlias extends Aa {
        |  val t = foo()
        |
        |  type K = <selection>this.type</selection>
        |
        |  def y(): Int = 3
        |}
      """
    val methodName: String = "K"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideValue() {
    val fileText =
      """
        |package test
        |
        |class A {
        |  val foo: A = new A
        |}
        |class OverrideValue extends A {
        |  val t = foo()
        |  <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class A {
        |  val foo: A = new A
        |}
        |class OverrideValue extends A {
        |  val t = foo()
        |  override val foo: A = <selection>_</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testImplementVar() {
    val fileText =
      """
        |package test
        |
        |trait A {
        |  var foo: A
        |}
        |class VarImplement extends A {
        |  val t = foo()
        |  <caret>
        |  def y(): Int = 3
        |}
      """
    val expectedText =
      """
        |package test
        |
        |trait A {
        |  var foo: A
        |}
        |class VarImplement extends A {
        |  val t = foo()
        |
        |  var foo: A = <selection>_</selection>
        |
        |  def y(): Int = 3
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testImplementFromSelfType() {
    val fileText =
      """
        |package test
        |
        |trait A {
        |  def foo: Int
        |}
        |trait B {
        |  self: A =>
        |  <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |trait A {
        |  def foo: Int
        |}
        |trait B {
        |  self: A =>
        |  def foo: Int = <selection>???</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideFromSelfType() {
    val fileText =
      """
        |package test
        |
        |trait A {
        |  def foo: Int = 1
        |}
        |trait B {
        |  self: A =>
        |  <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |trait A {
        |  def foo: Int = 1
        |}
        |trait B {
        |  self: A =>
        |  override def foo = <selection>self.foo</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))
    runTest(methodName, fileText, expectedText, isImplement, settings = TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
  }

  def testTypeAlias() {
    val fileText =
      """
        |class ImplementTypeAlias extends b {
        |  <caret>
        |}
        |abstract class b {
        |  type L
        |}
      """
    val expectedText =
      """
        |class ImplementTypeAlias extends b {
        |  type L = <selection>this.type</selection>
        |}
        |abstract class b {
        |  type L
        |}
      """
    val methodName: String = "L"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testVal() {
    val fileText =
      """
        |package test
        |
        |class Val extends b {
        |  <caret>
        |}
        |abstract class b {
        |  val too: b
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Val extends b {
        |  val too: b = <selection>_</selection>
        |}
        |abstract class b {
        |  val too: b
        |}
      """
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testVar() {
    val fileText =
      """
        |package test
        |
        |class Var extends b {
        |  <caret>
        |}
        |abstract class b {
        |  var too: b
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Var extends b {
        |  var too: b = <selection>_</selection>
        |}
        |abstract class b {
        |  var too: b
        |}
      """
    val methodName: String = "too"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testClassTypeParam() {
    val fileText =
      """
        |class A[T] {
        |  def foo: T = new T
        |}
        |
        |class ClassTypeParam extends A[Int] {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A[T] {
        |  def foo: T = new T
        |}
        |
        |class ClassTypeParam extends A[Int] {
        |  override def foo: Int = <selection>super.foo</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testHardSubstituting() {
    val fileText =
      """
        |class A[T] {
        |  def foo(x: (T) => T, y: (T, Int) => T): Double = 1.0
        |}
        |
        |class Substituting extends A[Float] {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A[T] {
        |  def foo(x: (T) => T, y: (T, Int) => T): Double = 1.0
        |}
        |
        |class Substituting extends A[Float] {
        |  override def foo(x: Float => Float, y: (Float, Int) => Float): Double = <selection>super.foo(x, y)</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSimpleTypeParam() {
    val fileText =
      """
        |abstract class A {
        |  def foo[T](x: T): T
        |}
        |class SimpleTypeParam extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |abstract class A {
        |  def foo[T](x: T): T
        |}
        |class SimpleTypeParam extends A {
        |  def foo[T](x: T): T = <selection>???</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL1997() {
    val fileText =
      """
        |package test
        |
        |trait Foo {
        |  def foo(a: Any*): Any
        |}
        |
        |trait Sub extends Foo {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |trait Foo {
        |  def foo(a: Any*): Any
        |}
        |
        |trait Sub extends Foo {
        |  def foo(a: Any*): Any = <selection>???</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL1999() {
    val fileText =
      """
        |package test
        |
        |trait Parent {
        |  def m(p: T forSome {type T <: Number})
        |}
        |
        |class Child extends Parent {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |trait Parent {
        |  def m(p: T forSome {type T <: Number})
        |}
        |
        |class Child extends Parent {
        |  def m(p: (T) forSome {type T <: Number}): Unit = <selection>???</selection>
        |}
      """
    val methodName: String = "m"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2540() {
    val fileText =
      """
        |class A {
        |  def foo(x_ : Int) = 1
        |}
        |
        |class B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A {
        |  def foo(x_ : Int) = 1
        |}
        |
        |class B extends A {
        |  override def foo(x_ : Int): Int = <selection>super.foo(x_)</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2010() {
    val fileText =
      """
        |package test
        |
        |class Parent {
        |  def doSmth(smth: => String) {}
        |}
        |
        |class Child extends Parent {
        | <caret>
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Parent {
        |  def doSmth(smth: => String) {}
        |}
        |
        |class Child extends Parent {
        |  override def doSmth(smth: => String): Unit = <selection>super.doSmth(smth)</selection>
        |}
      """
    val methodName: String = "doSmth"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2052A() {
    val fileText =
      """
        |class A {
        |  type ID[X] = X
        |  def foo(in: ID[String]): ID[Int] = null
        |}
        |
        |class B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A {
        |  type ID[X] = X
        |  def foo(in: ID[String]): ID[Int] = null
        |}
        |
        |class B extends A {
        |  override def foo(in: ID[String]): ID[Int] = <selection>super.foo(in)</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2052B() {
    val fileText =
      """
        |class A {
        |  type ID[X] = X
        |  val foo: ID[Int] = null
        |}
        |
        |class B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A {
        |  type ID[X] = X
        |  val foo: ID[Int] = null
        |}
        |
        |class B extends A {
        |  override val foo: ID[Int] = <selection>_</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL2052C() {
    val fileText =
      """
        |class A {
        |  type F = (Int => String)
        |  def foo(f: F): Any = null
        |}
        |
        |object B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A {
        |  type F = (Int => String)
        |  def foo(f: F): Any = null
        |}
        |
        |object B extends A {
        |  override def foo(f: B.F): Any = <selection>super.foo(f)</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL3808() {
    val fileText =
      """
        |trait TC[_]
        |
        |class A {
        |  def foo[M[X], N[X[_]]: TC]: String = ""
        |}
        |
        |object B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |trait TC[_]
        |
        |class A {
        |  def foo[M[X], N[X[_]]: TC]: String = ""
        |}
        |
        |object B extends A {
        |  override def foo[M[X], N[X[_]] : TC]: String = <selection>super.foo</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testSCL3305() {
    val fileText =
      """
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
        |  <caret>
        |}
      """
    val expectedText =
      """
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
        |  def foo(v: Nested.Nested2): Unit = <selection>???</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testUnitReturn() {
    val fileText =
      """
        |package test
        |
        |class Foo extends b {
        |  <caret>
        |}
        |abstract class b {
        |  def foo(x: b): Unit
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Foo extends b {
        |  def foo(x: b): Unit = <selection>???</selection>
        |}
        |abstract class b {
        |  def foo(x: b): Unit
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testUnitInferredReturn() {
    val fileText =
      """
        |package test
        |
        |class Foo extends b {
        |  <caret>
        |}
        |abstract class b {
        |  def foo(x: b) = ()
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Foo extends b {
        |  override def foo(x: b): Unit = <selection>super.foo(x)</selection>
        |}
        |abstract class b {
        |  def foo(x: b) = ()
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testInferredReturn() {
    val fileText =
      """
        |package test
        |
        |class Foo extends b {
        |  <caret>
        |}
        |abstract class b {
        |  def foo(x: b) = 1
        |}
      """
    val expectedText =
      """
        |package test
        |
        |class Foo extends b {
        |  override def foo(x: b): Int = <selection>super.foo(x)</selection>
        |}
        |abstract class b {
        |  def foo(x: b) = 1
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testNoExplicitReturn() {
    val fileText =
      """
        |class A {
        |  def foo(x : Int): Int = 1
        |}
        |
        |class B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class A {
        |  def foo(x : Int): Int = 1
        |}
        |
        |class B extends A {
        |  override def foo(x: Int): Int = <selection>super.foo(x)</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))
   
    runTest(methodName, fileText, expectedText, isImplement, settings)
  }

  def testImplicitParams() {
    val fileText =
      """
        |trait A {
        |  def foo(x : Int)(implicit name: String): Int = name + x
        |}
        |
        |class B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |trait A {
        |  def foo(x : Int)(implicit name: String): Int = name + x
        |}
        |
        |class B extends A {
        |  override def foo(x: Int)(implicit name: String): Int = <selection>super.foo(x)</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  //don't add return type for protected
  def testProtectedMethod() {
    val fileText =
      """
        |abstract class A {
        |  protected def foo(): Unit
        |}
        |
        |class B extends A {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |abstract class A {
        |  protected def foo(): Unit
        |}
        |
        |class B extends A {
        |  protected def foo() = <selection>???</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = true

    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))
    
    runTest(methodName, fileText, expectedText, isImplement, settings = TypeAnnotationSettings.noTypeAnnotationForProtected(settings))
  }

  def testProtectedMethodNoBody() {
    val fileText =
      """
        |abstract class A {
        |  protected def foo(): Unit
        |}
        |
        |class B<caret> extends A
      """
    val expectedText =
      """
        |abstract class A {
        |  protected def foo(): Unit
        |}
        |
        |class B extends A {
        |  protected def foo(): Unit = <selection>???</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideProtectedMethodNoBody() {
    val fileText =
      """
        |abstract class A {
        |  protected def foo(): Unit = {}
        |}
        |
        |class B<caret> extends A
      """
    val expectedText =
      """
        |abstract class A {
        |  protected def foo(): Unit = {}
        |}
        |
        |class B extends A {
        |  override protected def foo(): Unit = <selection>super.foo()</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }


  def testCopyScalaDoc() = {
    val fileText =
      """
        |abstract class A {
        |
        |  /**
        |    * qwerty
        |    *
        |    * @return
        |    */
        |  protected def foo(): Unit = {}
        |}
        |
        |class B<caret> extends A
      """
    val expectedText =
      """
        |abstract class A {
        |
        |  /**
        |    * qwerty
        |    *
        |    * @return
        |    */
        |  protected def foo(): Unit = {}
        |}
        |
        |class B extends A {
        |  /**
        |    * qwerty
        |    *
        |    * @return
        |    */
        |  override protected def foo(): Unit = <selection>super.foo()</selection>
        |}
      """
    val methodName: String = "foo"
    val isImplement = false
    val copyScalaDoc = true
    runTest(methodName, fileText, expectedText, isImplement, copyScalaDoc = copyScalaDoc)
  }

  def testNoImportScalaSeq(): Unit = {
    val fileText =
      """
        |import scala.collection.Seq
        |
        |class Test {
        |  def foo: Seq[Int] = Seq(1)
        |}
        |
        |class Test2 extends Test {
        |<caret>
        |}
      """
    val expectedText =
      """
        |import scala.collection.Seq
        |
        |class Test {
        |  def foo: Seq[Int] = Seq(1)
        |}
        |
        |class Test2 extends Test {
        |  override def foo: Seq[Int] = super.foo
        |}
      """

    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testOverrideClassParam(): Unit = {
    val fileText =
      """
        |class Parent(val param1: Int, var param2: String)
        |
        |class Child extends Parent(4, "") {
        |  <caret>
        |}
      """

    val expectedText =
      """
        |class Parent(val param1: Int, var param2: String)
        |
        |class Child extends Parent(4, "") {
        |  override val param1: Int = _
        |}
      """

    runTest("param1", fileText, expectedText, isImplement = false)
  }

  def testDoNotSaveAnnotations(): Unit ={
    val fileText =
      """
        |trait Base {
        |  @throws(classOf[Exception])
        |  @deprecated
        |  def annotFoo(int: Int): Int = 45
        |}
        |
        |class Inheritor extends Base {
        | <caret>
        |}
      """

    val expectedText =
      """
        |trait Base {
        |  @throws(classOf[Exception])
        |  @deprecated
        |  def annotFoo(int: Int): Int = 45
        |}
        |
        |class Inheritor extends Base {
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}
      """

    val methodName: String = "annotFoo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement)
  }

  def testInfixTypeAlias(): Unit = {
    val fileText =
      """
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
        |  <caret>
        |}
      """
    val expectedText =
      """
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
      """
    val methodName = "deleteUser"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement)
  }
}
