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
    doPosTest(code)
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
    doPosTest(code)
  }

  def testFunctionSAM() {
    val code =
      """
        |def z() = println()
        |val y: Runnable = z
      """.stripMargin
    doPosTest(code)
  }

  def testFunctionNegOne() {
    val code =
      """
        |def z(): Unit = println()
        |val y: Runnable = z()
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z()", typeMismatch()) :: Error("z()", doesNotConform()) :: Error("Runnable", null) :: Nil =>
    }
  }

  def testFunctionNegTwo() {
    val code =
      """
        |def z: Unit = println()
        |val y: Runnable = z
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z", typeMismatch()) :: Error("z", doesNotConform()) :: Error("Runnable", null) :: Nil =>
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
      case Error("x", typeMismatch()) :: Error("x", doesNotConform()) :: Error("Runnable", null) :: Nil =>
    }
  }
  
  def testSCL7686(): Unit = {
    val code =
      """
        |trait FI { def apply(idx: Int): String }
        |val a: FI = x => "result: " + x.toString
        |println(a(5))
      """.stripMargin
    doPosTest(code)
  }

  def testUnderscoreOne() {
    val code =
      """
        |trait Foo { def bar(i: Int, s: String): String }
        |val f: Foo = _ + _
      """.stripMargin
    doPosTest(code)
  }

  def testUnderscoreTwo() {
    val code =
      """
        |trait Foo { def bar(s: String): String }
        |val i: Foo = _.charAt(0).toString
      """.stripMargin
    doPosTest(code)
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
        Error("Blergh", null) :: Nil =>
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
      case Error("((i: Int) => \"aaa\")", typeMismatch()) :: Error("((i: Int) => \"aaa\")", doesNotConform()) ::
        Error("Blargle", null) :: Nil =>
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
      case Error("((i: Int, j: Int) => \"aaa\")", typeMismatch()) :: Error("((i: Int, j: Int) => \"aaa\")", doesNotConform()) ::
        Error("Blargle", null) :: Nil =>
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
      case Error("(j => j)", typeMismatch()) :: Error("(j => j)", doesNotConform()) ::
        Error("Blergh", null) :: Nil =>
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
    doPosTest(code)
  }

  def testConformance(): Unit = {
    val code =
      """
        |trait SAAM {
        |  def sam(s: String): Object
        |}
        |val s: SAAM = (i: Object) => ""
      """.stripMargin
    doPosTest(code)
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
    doPosTest(code)
  }

  
  def doPosTest(code: String) {
    assertMatches(messages(code)) {
      case Nil =>
    }
  }

  def messages(code: String): List[Message] = {
    val annotator = new ScalaAnnotator() {}
    val mock = new AnnotatorHolderMock

    val parse: ScalaFile = parseText(code)

    parse.depthFirst.foreach(annotator.annotate(_, mock))

    mock.annotations.filterNot(_.isInstanceOf[Info])
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

  case class ContainsPattern(fr: String) {
    def unapply(s: String) = s.contains(fr)
  }
}

