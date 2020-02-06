package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 6/15/15
  */
abstract class SingleAbstractMethodTestBase extends ScalaFixtureTestCase with MatcherAssertions {



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
      case Error("z()", doesNotConform()) :: Nil =>
    }
  }

  def testFunctionNegTwo() {
    val code =
      """
        |def z: Unit = println()
        |val y: Runnable = z
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z", doesNotConform()) :: Nil =>
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
      case Error("x", doesNotConform()) :: Nil =>
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
      case Error("Blergh", cannotUpcast()) :: Nil =>
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
      case Error("t)", "Missing parameter: String") :: Nil =>
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
      case Error("Int", typeMismatch()) :: Nil =>
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
      case Error("Blergh", cannotUpcast()) :: Nil =>
    }
  }

  def testConstructorWithArgs() {
    val code =
      """
        |abstract class Foo(s: String) { def a(): String }
        |val f: Foo = () => ""
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("() => \"\"", doesNotConform()) :: Nil =>
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
      case Error("() => 3", doesNotConform()) :: Nil =>
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
      case Error("String => i", typeMismatch()) :: Nil =>
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

  def testSCL11156_1(): Unit = {
    val code =
      """
        |trait F[T, R] {
        |  def apply(a: T): R
        |}
        |
        |trait Specific extends F[String, Int]
        |
        |val ok: F[Int, Int] = _ => 1
        |val error: Specific = _ => 1
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSCL11156_2(): Unit = {
    val code =
      """
        |object Test {
        |
        |  trait Parser[T] extends (String => T)
        |
        |  val item: Parser[Char] = _ => 'q'
        |}
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSCL11156_Java(): Unit = {
    val javaCode = Some {
      "public interface StringSupplier extends java.util.function.Supplier<String> {}"
    }
    val code =
      """val x: StringSupplier = () => "ab" """
    checkCodeHasNoErrors(code, javaCode)
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
      case Error("a", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("a()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("b()", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("c", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("()", "c does not take parameters") ::
        Error("d()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("e", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("e()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f()", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("g()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("h()", "Expression of type Unit doesn't conform to expected type Runnable") :: Nil =>
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
      case Error("TwoConstrucorParamLists", cannotUpcast()) :: Nil =>
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
      case Error("(s: String) => ???", doesNotConform()) :: Nil =>
    }
  }

  def testSCL11064(): Unit = {
    val code =
      """
        |import scala.language.higherKinds
        |
        |object FBound212 {
        |  trait Parser[A, F[X] <: Parser[X, F]] {
        |    def parse(s: String): A
        |
        |    def map[B](f: A ⇒ B): F[B]
        |  }
        |
        |  trait Foo[A] extends Parser[A, Foo]{
        |    self ⇒
        |    override def map[B](f: (A) ⇒ B) = s ⇒ f(self.parse(s))
        |  }
        |}
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def checkCodeHasNoErrors(scalaCode: String, javaCode: Option[String] = None) {
    assertNothing(messages(scalaCode, javaCode))
  }

  def messages(@Language("Scala") scalaCode: String, javaCode: Option[String] = None): List[Message] = {
    javaCode match {
      case Some(s) => myFixture.addFileToProject("dummy.java", s)
      case _ =>
    }

    val annotator = ScalaAnnotator.forProject
    val file: ScalaFile = parseText(scalaCode)
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotator.annotate)

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
  val cannotUpcast = ContainsPattern("Cannot upcast")
  val doesNotTakeParameters = ContainsPattern("does not take parameters")
  val missingParameterType = ContainsPattern("Missing parameter type")

  case class ContainsPattern(fr: String) {
    def unapply(s: String): Boolean = s.contains(fr)
  }

}

class SingleAbstractMethodTest extends SingleAbstractMethodTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_12

  def testFunctionSAM() {
    val code =
      """
        |def z() = println()
        |val y: Runnable = z
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z", doesNotConform()) :: Nil =>
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
      case Error("b", doesNotConform()) ::
        Error("d", doesNotConform()) ::
        Error("f", doesNotConform()) ::
        Error("g", doesNotConform()) ::
        Error("h", doesNotConform()) :: Nil =>
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
      case Error("foo", doesNotConform()) :: Nil =>
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
      case Error("foo", doesNotConform()) :: Nil =>
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
      case Error("P[_ <: T]", typeMismatch()) :: Nil =>
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
      case Error("SelfTp", cannotUpcast()) :: Nil =>
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

class SingleAbstractMethodTest_2_11 extends SingleAbstractMethodTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_11

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

  def testClassWithOverriddenAbstract(): Unit = {
    val javaCode =
      """
        |public class Abstracts {
        |    public static abstract class Base {
        |        public abstract void first(String s);
        |    }
        |
        |    public static abstract class Derived extends Base {
        |        @Override
        |        public void first(String s) {
        |            second(s);
        |        }
        |
        |        public abstract void second(String s);
        |    }
        |}
      """.stripMargin
    val code =
      """
        |object Test {
        |  def foo(x: Abstracts.Base) = ???
        |  def bar(x: Abstracts.Derived) = ???
        |
        |  foo(_ => ())
        |  bar(_ => ())
        |}
      """.stripMargin

    checkCodeHasNoErrors(code, Some(javaCode))
  }
}