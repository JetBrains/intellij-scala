package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 6/15/15
 */
class SingleAbstractMethodTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected override def setUp() {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProjectAdapter).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.experimental = true
    defaultProfile.setSettings(newSettings)
  }

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

  def testFunctionSAM() {
    val code =
      """
        |def z() = println()
        |val y: Runnable = z
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
      case Error("z()", typeMismatch()) :: Error("z()", doesNotConform()):: Nil =>
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
        |
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

  val etaExpansionPrefix: String =
    """
      |def a = () => println()
      |def b() = () => println()
      |def c = println()
      |def d() = println()
      |def e: () => Unit = () => println()
      |def f(): () => Unit = () => println()
      |def g(): Unit = println()
      |def h(): Unit = println()
      |
    """.stripMargin

  def testSAMEtaExpansion1(): Unit = {
    val code = etaExpansionPrefix + "val a1: Runnable = a"
    assertMatches(messages(code)) {
      case Error("a", typeMismatch()) :: Error("a", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion2(): Unit = {
    val code = etaExpansionPrefix + "val a2: Runnable = a()"
    assertMatches(messages(code)) {
      case Error("a()", typeMismatch()) :: Error("a()", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion3(): Unit = {
    val code = etaExpansionPrefix + "val b1: Runnable = b"
    checkCodeHasNoErrors(code)
  }

  def testSAMEtaExpansion4(): Unit = {
    val code = etaExpansionPrefix + "val b2: Runnable = b()"
    assertMatches(messages(code)) {
      case Error("b()", typeMismatch()) :: Error("b()", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion5(): Unit = {
    val code = etaExpansionPrefix + "val c1: Runnable = c"
    assertMatches(messages(code)) {
      case Error("c", typeMismatch()) :: Error("c", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion6(): Unit = {
    val code = etaExpansionPrefix + "val c2: Runnable = c()"
    assertMatches(messages(code)) {
      case Error("()", doesNotTakeParameters()) :: Nil =>
    }
  }

  def testSAMEtaExpansion7(): Unit = {
    val code = etaExpansionPrefix + "val d1: Runnable = d"
    checkCodeHasNoErrors(code)
  }

  def testSAMEtaExpansion8(): Unit = {
    val code = etaExpansionPrefix + "val d2: Runnable = d()"
    assertMatches(messages(code)) {
      case Error("d()", typeMismatch()) :: Error("d()", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion9(): Unit = {
    val code = etaExpansionPrefix + "val e1: Runnable = e"
    assertMatches(messages(code)) {
      case Error("e", typeMismatch()) :: Error("e", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion10(): Unit = {
    val code = etaExpansionPrefix + "val e2: Runnable = e()"
    assertMatches(messages(code)) {
      case Error("e()", typeMismatch()) :: Error("e()", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion11(): Unit = {
    val code = etaExpansionPrefix + "val f1: Runnable = f"
    checkCodeHasNoErrors(code)
  }

  def testSAMEtaExpansion12(): Unit = {
    val code = etaExpansionPrefix + "val f2: Runnable = f()"
    assertMatches(messages(code)) {
      case Error("f()", typeMismatch()) :: Error("f()", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion13(): Unit = {
    val code = etaExpansionPrefix + "val g1: Runnable = g"
    checkCodeHasNoErrors(code)
  }

  def testSAMEtaExpansion14(): Unit = {
    val code = etaExpansionPrefix + "val g2: Runnable = g()"
    assertMatches(messages(code)) {
      case Error("g()", typeMismatch()) :: Error("g()", doesNotConform()) :: Nil =>
    }
  }

  def testSAMEtaExpansion15(): Unit = {
    val code = etaExpansionPrefix + "val h1: Runnable = h"
    checkCodeHasNoErrors(code)
  }

  def testSAMEtaExpansion16(): Unit = {
    val code = etaExpansionPrefix + "val h2: Runnable = h()"
    assertMatches(messages(code)) {
      case Error("h()", typeMismatch()) :: Error("h()", doesNotConform()) :: Nil =>
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
        |
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  //similar to testEtaExpansion11
  def testEtaExpansionUnitReturnWithParams(): Unit = {
    val code =
      """
        |trait S {
        |  def foo(i: Int): Unit
        |}
        |def ss(): Int => Unit = (i: Int) => Unit
        |
        |val s: S = ss
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("ss", typeMismatch()) :: Error("ss", doesNotConform()) :: Nil =>
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
        |
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

  def checkCodeHasNoErrors(scalaCode: String, javaCode: Option[String] = None) {
    assertMatches(messages(scalaCode, javaCode)) {
      case Nil =>
    }
  }

  def messages(@Language("Scala") scalaCode: String, javaCode: Option[String] = None): List[Message] = {
    javaCode match {
      case Some(s) => configureFromFileTextAdapter("dummy.java", s)
      case _ =>
    }

    val annotator = new ScalaAnnotator() {}
    val mock = new AnnotatorHolderMock

    val parse: ScalaFile = parseText(scalaCode)

    parse.depthFirst.foreach(annotator.annotate(_, mock))

    mock.errorAnnotations.filter {
      case Error(_, null) => false
      case _ => true
    }
  }

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]) {
    Assert.assertTrue("actual: " + actual.toString, pattern.isDefinedAt(actual))
  }

  def parseText(@Language("Scala") s: String): ScalaFile = {
    PsiFileFactory.getInstance(getProjectAdapter)
      .createFileFromText("foo" + ScalaFileType.DEFAULT_EXTENSION, ScalaFileType.SCALA_FILE_TYPE, s)
      .asInstanceOf[ScalaFile]
  }

  val cannotResolveSymbol = ContainsPattern("Cannot resolve symbol")
  val doesNotConform = ContainsPattern("doesn't conform to expected type")
  val typeMismatch = ContainsPattern("Type mismatch")
  val cannotResolveReference = ContainsPattern("Cannot resolve reference")
  val doesNotTakeParameters = ContainsPattern("does not take parameters")

  case class ContainsPattern(fr: String) {
    def unapply(s: String) = s.contains(fr)
  }
}

