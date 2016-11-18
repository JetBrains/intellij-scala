package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 6/15/15
  */
abstract class SingleAbstractMethodTestBase(scalaSdk: ScalaSdkVersion = TestUtils.DEFAULT_SCALA_SDK_VERSION)
  extends ScalaFixtureTestCase(scalaSdk) with AssertMatches {
  def testBasicGenerics() {
    val code =
      """
        |trait Blargle[T] {
        |  def blargle(a: T): Unit
        |}
        |def foo(a: Blargle[String]) = a.blargle("10")
        |foo(x => println(x.charAt(0)))
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testTypeInference() {
    val code =
      """
        | abstract class Foo {
        |   def bar(i: Int, j: String)
        | }
        |
        | val b: Foo = (i, j) => println(i + j.charAt(0))
      """.stripMargin
    checkCodeHasNoErrors(code)
  }


  def testFunctionNegOne() {
    val code =
      """
        |def z(): Unit = println()
        |val y: Runnable = z()
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z()", typeMismatch()) :: Error("z()", doesNotConform()) :: Nil =>
    }
  }

  def testFunctionNegTwo() {
    val code =
      """
        |def z: Unit = println()
        |val y: Runnable = z
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z", typeMismatch()) :: Error("z", doesNotConform()) :: Nil =>
    }
  }

  def testFunctionNegThree() {
    val code =
      """
        |def z(): Unit = println()
        |val x = z
        |val y: Runnable = x
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("x", typeMismatch()) :: Error("x", doesNotConform()) :: Nil =>
    }
  }

  def testSCL7686(): Unit = {
    val code =
      """
        |trait FI { def apply(idx: Int): String }
        |val a: FI = x => "result: " + x.toString
        |println(a(5))
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testUnderscoreOne() {
    val code =
      """
        |trait Foo { def bar(i: Int, s: String): String }
        |val f: Foo = _ + _
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testUnderscoreTwo() {
    val code =
      """
        |trait Foo { def bar(s: String): String }
        |val i: Foo = _.charAt(0).toString
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSimpleNeg() {
    val code =
      """
        |trait Foo { def blargle(i: Int): Unit }
        |val f: Foo = s => println(s.charAt(0))
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("charAt", cannotResolveSymbol()) :: Nil =>
    }
  }

  def testSimpleNegWrongReturnType() {
    val code =
      """
        |object T {
        |  trait Blergh { def apply(i: Int): String }
        |  ((j: Int) => j): Blergh
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("((j: Int) => j)", typeMismatch()) :: Error("((j: Int) => j)", doesNotConform()) ::
        Error("j", doesNotConform()) :: Nil =>
    }
  }

  def testSimpleNegWrongParamNumber() {
    val code =
      """
        |object T {
        |  trait Blargle { def apply(i: Int, j: String): String }
        |  ((i: Int) => "aaa"): Blargle
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("((i: Int) => \"aaa\")", typeMismatch()) :: Error("((i: Int) => \"aaa\")", doesNotConform()) :: Nil =>
    }
  }

  def testSimpleNegWrongParamType() {
    val code =
      """
        |object T {
        |  trait Blargle { def apply(i: Int, j: String): String }
        |  ((i: Int, j: Int) => "aaa"): Blargle
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("((i: Int, j: Int) => \"aaa\")", typeMismatch()) :: Error("((i: Int, j: Int) => \"aaa\")", doesNotConform()) :: Nil =>
    }
  }

  def testSimpleNegRightParamWrongReturn() {
    val code =
      """
        |object T {
        |  trait Blergh { def apply(i: Int): String }
        |  (j => j): Blergh
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("(j => j)", typeMismatch()) :: Error("(j => j)", doesNotConform()) :: Error("j", doesNotConform()) :: Nil =>
    }
  }

  def testConstructorWithArgs() {
    val code =
      """
        |abstract class Foo(s: String) { def a(): String }
        |val f: Foo = () => ""
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("() => \"\"", typeMismatch()) :: Nil =>
    }
  }

  def testImplicitConversionWithSAM() {
    val code =
      """
        |import scala.language.implicitConversions
        |object T {
        |  trait Foo {
        |    def bar(): Int
        |  }
        |
        |  val i: Foo = () => 2
        |
        |  implicit def FooToString(f: Foo): String = f.bar().toString
        |  wantFoo(i)
        |  wantString(i)
        |  wantFoo(() => 4)
        |  wantString(() => 3)
        |  def wantFoo(f: Foo) = println(f.bar())
        |  def wantString(s: String) = println(s)
        |}
        |
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("() => 3", typeMismatch()) :: Error("wantString", cannotResolveReference()) :: Nil =>
    }
  }

  def testUnimplementedWithSAM(): Unit = {
    val code =
      """
        |abstract class Foo { def a(): String }
        |val f: Foo = () => ???
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testConformance(): Unit = {
    val code =
      """
        |trait SAAM {
        |  def sam(s: String): Object
        |}
        |val s: SAAM = (i: Object) => ""
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testConformanceNeg(): Unit = {
    val code =
      """
        |trait SAAM {
        |  def sam(s: Object): Object
        |}
        |val s: SAAM = (i: String) => i
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("(i: String) => i", typeMismatch()) :: Nil =>
    }
  }

  def testSimpleThreadRunnable(): Unit = {
    val code = "new Thread(() => println()).run()"
    checkCodeHasNoErrors(code)
  }

  def testValueDiscarding(): Unit = {
    val code =
      """
        |def goo(r: Runnable) = 2
        |
        |
        |goo(() => {1 + 1})
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testJavaGenerics(): Unit = {
    val code =
      """
        |import java.util.concurrent.FutureTask
        |
        |new FutureTask[String](() => "hi")
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMMethodReference(): Unit = {
    val code =
      """
        |trait F[T, R] {
        |  def apply(a: T): R
        |}
        |
        |def len(s: String): Int  = s.length
        |
        |val f: F[String, Int] = len
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testExistentialBounds(): Unit = {
    val code =
      """
        |trait Blargle[T] {
        |  def foo(a: T): String
        |}
        |
        |def f(b: Blargle[_ >: Int]) = -1
        |f(s => s.toString)
        |
        |def g[T](b: Blargle[_ >: T]) = -1
        |g((s: String) => s)
        |
        |trait Blergh[T] {
        |  def foo(): T
        |}
        |
        |def h[T](b: Blergh[_ <: T]) = -1
        |h(() => "")
        |def i(b: Blergh[_ <: String]) = -1
        |i(() => "")
        |
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testOverload(): Unit = {
    val code =
      """
        |trait SAMOverload[A] {
        |  def foo(s: A): Int = ???
        |}
        |
        |def f[T](s: T): Unit = ()
        |def f[T](s: T, a: SAMOverload[_ >: T]) = ()
        |f("", (s: String) => 2)
        |
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testJavaSAM(): Unit = {
    val scalaCode = "new ObservableCopy(1).mapFunc(x => x + 1)"
    val javaCode =
      """
        |public interface Func1<T, R> {
        |    R call(T t);
        |}
        |
        |public class ObservableCopy<T> {
        |    public ObservableCopy(T t) {}
        |
        |    public final <R> ObservableCopy<R> mapFunc(Func1<? super T, ? extends R> func) {
        |        return null;
        |    }
        |}
        |
      """.stripMargin
    checkCodeHasNoErrors(scalaCode, Some(javaCode))
  }

  val etaExpansionTestPrefix: String =
    """
      |def a = () => println()
      |def b() = () => println()
      |def c = println()
      |def d() = println()
      |def e: () => Unit = () => println()
      |def f(): () => Unit = () => println()
      |def g(): Unit = println()
      |def h(): Unit = println()
    """.stripMargin


  def testSAMInvalidEtaExpansion(): Unit = {
    val code = etaExpansionTestPrefix +
      """
        |val a1: Runnable = a
        |val a2: Runnable = a()
        |val b2: Runnable = b()
        |val c1: Runnable = c
        |val c2: Runnable = c()
        |val d2: Runnable = d()
        |val e1: Runnable = e
        |val e2: Runnable = e()
        |val f2: Runnable = f()
        |val g2: Runnable = g()
        |val h2: Runnable = h()
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("a", typeMismatch()) :: Error("a", doesNotConform()) ::
        Error("a()", typeMismatch()) :: Error("a()", doesNotConform()) ::
        Error("b()", typeMismatch()) :: Error("b()", doesNotConform()) ::
        Error("c", typeMismatch()) :: Error("c", doesNotConform()) ::
        Error("()", doesNotTakeParameters()) :: Error("c", cannotResolveReference()) ::
        Error("d()", typeMismatch()) :: Error("d()", doesNotConform()) ::
        Error("e", typeMismatch()) :: Error("e", doesNotConform()) ::
        Error("e()", typeMismatch()) :: Error("e()", doesNotConform()) ::
        Error("f()", typeMismatch()) :: Error("f()", doesNotConform()) ::
        Error("g()", typeMismatch()) :: Error("g()", doesNotConform()) ::
        Error("h()", typeMismatch()) :: Error("h()", doesNotConform()) :: Nil =>
    }
  }

  def testOverrideImplementSAM(): Unit = {
    val code =
      """
        |val s: Bar = () => 2
        |
        |abstract class Foo {
        |  def foo(): Int
        |}
        |
        |abstract class Bar extends Foo
        |
      """.stripMargin

    checkCodeHasNoErrors(code)
  }

  def testOverrideImplementSAM2(): Unit = {
    val code =
      """
        |val s: Bar = () => 2
        |
        |abstract class Foo {
        |  def foo2(): Int
        |}
        |
        |abstract class Bar extends Foo {
        |  def foo1(): String = ""
        |}
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMComparable(): Unit = {
    val code =
      """
        |import java.util.Comparator
        |
        |val comp: Comparator[String] = (o1, o2) => o1.compareTo(o2)
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testNotSAM(): Unit = {
    val code =
      """
        |abstract class U {
        |  def foo(): Unit
        |}
        |def z(): U = null
        |val x: U = z()
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMCorrectWildcardExtrapolationWithParameterizedTypes(): Unit = {
    val code =
      """
        |trait MyObservableValue[T] {
        |  def addListener(listener: MyChangeListener[_ >: T])
        |}
        |
        |trait MyChangeListener[T] {
        |  def changed(observable: MyObservableValue[T])
        |}
        |
        |val observableValue: MyObservableValue[Int] = ???
        |observableValue.addListener((observable: MyObservableValue[Int]) => ())
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testMultipleConstructorLists(): Unit = {
    val code =
      """
        |abstract class TwoConstrucorParamLists()() { def ap(a: Int): String }
        |
        |((x: Int) => x.toString()): TwoConstrucorParamLists
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("((x: Int) => x.toString())", typeMismatch()) :: Error("((x: Int) => x.toString())", doesNotConform()) :: Nil =>
    }
  }

  def testValidTwoConstructors(): Unit = {
    val code =
      """
        |abstract class TwoConstructors() {
        |  def this(a: Int) = {
        |    this()
        |    println()
        |  }
        |
        |  def s(a: Int): String
        |}
        |
        |((x: Int) => ""): TwoConstructors
      """.stripMargin

    checkCodeHasNoErrors(code)
  }

  def testNotSAMWithAbstractFields(): Unit = {
    val code =
      """
        |object Moo {
        |
        |  val zs: NotReallySAM = (s: String) => ???
        |
        |
        |  trait NotReallySAM {
        |    val koo: Int
        |    def foo(s: String): Int
        |  }
        |
        |  abstract class AbstractNotReallySAM(val koo: Int) extends NotReallySAM
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("(s: String) => ???", typeMismatch()) :: Nil =>
    }
  }

  def checkCodeHasNoErrors(scalaCode: String, javaCode: Option[String] = None) {
    assertNothing(messages(scalaCode, javaCode))
  }

  def messages(@Language("Scala") scalaCode: String, javaCode: Option[String] = None): List[Message] = {
    javaCode match {
      case Some(s) => myFixture.addFileToProject("dummy.java", s)
      case _ =>
    }

    val annotator = new ScalaAnnotator() {}
    val file: ScalaFile = parseText(scalaCode)
    val mock = new AnnotatorHolderMock(file)

    file.depthFirst.foreach(annotator.annotate(_, mock))

    mock.errorAnnotations.filter {
      case Error(_, null) => false
      case _ => true
    }
  }

  def parseText(@Language("Scala") s: String): ScalaFile = {
    myFixture.configureByText("foo.scala", s)
    myFixture.getFile.asInstanceOf[ScalaFile]
  }

  val cannotResolveSymbol = ContainsPattern("Cannot resolve symbol")
  val doesNotConform = ContainsPattern("doesn't conform to expected type")
  val typeMismatch = ContainsPattern("Type mismatch")
  val cannotResolveReference = ContainsPattern("Cannot resolve reference")
  val doesNotTakeParameters = ContainsPattern("does not take parameters")
  val missingParameterType = ContainsPattern("Missing parameter type")

  case class ContainsPattern(fr: String) {
    def unapply(s: String) = s.contains(fr)
  }

}

class SingleAbstractMethodTest extends SingleAbstractMethodTestBase(scalaSdk = ScalaSdkVersion._2_12_OLD) {
  def testFunctionSAM() {
    val code =
      """
        |def z() = println()
        |val y: Runnable = z
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z", typeMismatch()) :: Error("z", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansionInvalid212(): Unit = {
    val code = etaExpansionTestPrefix +
      """
        |val b1: Runnable = b
        |val d1: Runnable = d
        |val f1: Runnable = f
        |val g1: Runnable = g
        |val h1: Runnable = h
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("b", typeMismatch()) :: Error("b", doesNotConform()) ::
        Error("d", typeMismatch()) :: Error("d", doesNotConform()) ::
        Error("f", typeMismatch()) :: Error("f", doesNotConform()) ::
        Error("g", typeMismatch()) :: Error("g", doesNotConform()) ::
        Error("h", typeMismatch()) :: Error("h", doesNotConform()) :: Nil =>
    }
  }

  def testEtaExpansionImplicit(): Unit = {
    val code =
      """
        |class A
        |class B
        |implicit def a2b(a: A): B = new B
        |
        |abstract class C {
        |  def foo(): B
        |}
        |
        |def foo(): A = new A
        |
        |val u: C = foo
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("foo", typeMismatch()) :: Error("foo", doesNotConform()) :: Nil =>
    }
  }

  def testEtaExpansionImplicitNonEmpty(): Unit = {
    val code =
      """
        |object Moo {
        |  class A
        |  class B
        |  implicit def a2b(a: A): B = new B
        |
        |  abstract class C {
        |    def bar(j: Int): B
        |  }
        |
        |  def foo(i: Int): A = new A
        |
        |  val u: C = foo
        |}
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMNumericWidening(): Unit = {
    val code =
      """
        |  abstract class A {
        |    def foo(): Long
        |  }
        |
        |  def foo(): Int = 1
        |  val a: A = foo
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("foo", typeMismatch()) :: Error("foo", doesNotConform()) :: Nil =>
    }
  }

  def testSAMCorrectWildcardExtrapolationWithExistentialTypes(): Unit = {
    val code =
      """
        |class P[R]
        |
        |trait MyChangeListener[T] {
        |  def changed(observable: P[_ <: T])
        |}
        |
        |def l[T]: MyChangeListener[_ >: T] = (observable: P[_ <: T]) => ()
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("(observable: P[_ <: T]) => ()", typeMismatch()) :: Nil =>
    }
  }

  def testSelfTypeNotAllowed(): Unit = {
    val code =
      """
        |class Foo
        |abstract class SelfTp extends Foo { self: Foo =>
        |  def ap(a: Int): Any
        |}
        |
        |((x: Int) => x): SelfTp
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("((x: Int) => x)", typeMismatch()) :: Error("((x: Int) => x)", doesNotConform()) :: Nil =>
    }
  }

  def testSelfTpAllowed(): Unit = {
    val code =
      """
        |class Foo
        |abstract class SelfTp1 extends Foo { self: SelfTp1 =>
        |  def ap(a: Int): Any
        |}
        |
        |abstract class SelfTp2 extends Foo { self =>
        |  def ap(a: Int): Any
        |}
        |abstract class SelfTp3[T] extends Foo { self: SelfTp3[String] =>
        |  def ap(a: Int): Any
        |}
        |
        |((x: Int) => x): SelfTp1
        |((x: Int) => x): SelfTp2
        |((x: Int) => x): SelfTp3[Int] //this compiles with 2.12-M4
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testNoEtaExpansionFromMethodType(): Unit = {
    val code =
      """
        |package testNoEtaExpansionFromMethodType
        |
        |object Moo {
        |  def foo(request: Request123): Unit = {
        |    copyStreamContent(request.getInputStream)
        |  }
        |
        |  def copyStreamContent(inputStream: InputStream): Int = -1
        |}
        |
        |trait InputStream {
        |  def write(s: String): Unit = {}
        |  def bar(): Int
        |}
        |trait Request123 {
        |  def getInputStream(): InputStream
        |}
        |
      """.stripMargin
    checkCodeHasNoErrors(code, None)
  }
}

class SingleAbstractMethodTest_2_11 extends SingleAbstractMethodTestBase(scalaSdk = ScalaSdkVersion._2_11) {
  protected override def setUp() {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(myFixture.getProject).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.experimental = true
    defaultProfile.setSettings(newSettings)
  }


  def testFunctionSAM() {
    val code =
      """
        |def z() = println()
        |val y: Runnable = z
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMValidEtaExpansion211(): Unit = {
    val code =
      s"""
        |object T {
        |  $etaExpansionTestPrefix
        |  val b1: Runnable = b
        |  val d1: Runnable = d
        |  val f1: Runnable = f
        |  val g1: Runnable = g
        |  val h1: Runnable = h
        |}
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testEtaExpansionImplicit(): Unit = {
    val code =
      """
        |class A
        |class B
        |implicit def a2b(a: A): B = new B
        |
        |abstract class C {
        |  def foo(): B
        |}
        |
        |def foo(): A = new A
        |
        |val u: C = foo
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMNumericWidening(): Unit = {
    val code =
      """
        |  abstract class A {
        |    def foo(): Long
        |  }
        |
        |  def foo(): Int = 1
        |  val a: A = foo
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSAMCorrectWildcardExtrapolationWithExistentialTypes(): Unit = {
    val code =
      """
        |class P[R]
        |
        |trait MyChangeListener[T] {
        |  def changed(observable: P[_ <: T])
        |}
        |
        |def l[T]: MyChangeListener[_ >: T] = (observable: P[_ <: T]) => ()
      """.stripMargin

    checkCodeHasNoErrors(code)
  }
}

