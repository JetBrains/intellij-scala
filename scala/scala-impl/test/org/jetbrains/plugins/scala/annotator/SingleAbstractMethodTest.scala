package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

// NOTE: there are some false negatives comparing to compiler result for Scala 2.11 and 2.12:
// Those are due to compiler bugs:
// https://github.com/scala/bug/issues/12334
// https://github.com/scala/bug/issues/12333
// These bugs are considered "won't fix" becauese “everything works fine in 2.13.4"
// We could unify the behaviour to "reimplement" those bug, but it doesn't worth it.
// I did a POC and it complicates the code significantly.
//
// TODO: why type mismatch error messages are different in tests and equal in idea tooltips for these two methods:
//  val f3: () => String = ScalaAbstractClass.scalaStaticFoo0
//  foo0(ScalaAbstractClass.scalaStaticFoo0)
//  ?
//  In tests there are two variants of error message:
//  1. "Expression of type String doesn't conform to expected type () => String")
//  2. "Type mismatch, expected: () => String, actual: String"
//  in prod only 2nd variant can be observed
@Category(Array(classOf[TypecheckerTests]))
abstract class SingleAbstractMethodTestBase extends ScalaFixtureTestCase with MatcherAssertions {
  import Message._

  protected def checkCodeHasNoErrors(scalaCode: String, javaCode: Option[String] = None): Unit =
    assertNothing(messages(scalaCode, javaCode))

  protected def messages(@Language("Scala") scalaCode: String): List[Message] =
    messages(scalaCode, None)

  protected def messages(@Language("Scala") scalaCode: String, javaCode: Option[String]): List[Message] =
    messages(scalaCode, "foo.scala", javaCode)

  protected def messages(@Language("Scala") scalaCode: String, scalaFileName: String, javaCode: Option[String]): List[Message] = {
    javaCode match {
      case Some(s) => myFixture.addFileToProject("dummy.java", s)
      case _ =>
    }

    val annotator = new ScalaAnnotator()
    val file: ScalaFile = parseText(scalaCode, scalaFileName)
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotator.annotate)

    mock.errorAnnotations.filter {
      case Error(_, null) => false
      case _ => true
    }
  }

  // TODO: leave or remove? which approach is better: test concatenated messages text or each message as an element of Seq?
  //  concatenated version is more convenient when comparing diff in test tree view
  protected def messagesText(messages: Seq[Message]): String =
    messages.map(_.toString).mkString("\n")

  protected def cleanExpectedMessagesText(text: String): String = {
    val commentsDropped = text.linesIterator.filterNot(_.startsWith("//"))
    val withoutNewLines = commentsDropped.filterNot(_.isEmpty).mkString("\n")
    withoutNewLines
  }

  private def parseText(@Language("Scala") s: String, fileName: String): ScalaFile = {
    myFixture.configureByText(fileName, s)
    myFixture.getFile.asInstanceOf[ScalaFile]
  }

  val cannotResolveSymbol = ContainsPattern("Cannot resolve symbol")
  val doesNotConform = ContainsPattern("doesn't conform to expected type")
  val typeMismatch = ContainsPattern("Type mismatch")
  val cannotUpcast = ContainsPattern("Cannot upcast")
  val doesNotTakeParameters = ContainsPattern("does not take parameters")
  val missingParameterType = ContainsPattern("Missing parameter type")
  val missingArguments = ContainsPattern("Missing arguments for method")
}

// TODO: think how also test against actual compiler output
//  Scala compiler behaviour can vary very much from version to version in some cases.
//  And it's very fragile to only rely on manual code examples + expected result pairs creation.
//  Would be nice to validate that actual scala compiler does not or does produce errors.
//  (this is not only about tests but about any other type-inference/resolve-related tests)
abstract class SingleAbstractMethodTest_Since_2_11_experimental extends SingleAbstractMethodTestBase {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_11

  protected val EtaExpansionNumericWideningCode_SAMWithNoParametersClauses: String =
    """abstract class C0 {
      |  def foo: Long
      |}
      |
      |def foo0: Int = 1
      |val a: C0 = foo0
      |val b: C0 = foo0 _
      |""".stripMargin
  def testEtaExpansionNumericWidening_SAMWithNoParametersClauses(): Unit =
    assertMatches(messages(EtaExpansionNumericWideningCode_SAMWithNoParametersClauses)) {
      case Error("foo0", doesNotConform()) ::
        Error("foo0 _", doesNotConform()) :: Nil =>
    }

  protected val EtaExpansionNumericWideningCode_SAMWithEmptyParams: String =
    """abstract class C00 {
      |  def foo(): Long
      |}
      |
      |def foo00(): Int = 1
      |val a: C00 = foo00
      |val b: C00 = foo00 _
      |""".stripMargin
  def testEtaExpansionNumericWidening_SAMWithEmptyParams(): Unit = {
    // NOTE: in scala 2.11 (with experimental flag) the compiler produces error for explicit eta-expansion (val b: C1 = foo1 _)
    // but looks like the root issue is bug https://github.com/scala/bug/issues/12333
    // (see comment for SingleAbstractMethodTestBase)
    checkCodeHasNoErrors(EtaExpansionNumericWideningCode_SAMWithEmptyParams)
  }

  protected val EtaExpansionNumericWideningCode_SAMWithNonEmptyParams: String =
    """abstract class C1 {
      |  def foo(s: String): Long
      |}
      |def foo1(s: String): Int = 1
      |val a: C1 = foo1
      |val b: C1 = foo1 _
      |""".stripMargin
  def testEtaExpansionNumericWidening_SAMWithNonEmptyParams(): Unit = {
    // NOTE: in scala 2.11 (with experimental flag) the compiler produces error for explicit eta-expansion (val b: C1 = foo1 _)
    // but looks like the root issue is bug https://github.com/scala/bug/issues/12333
    // (see comment for SingleAbstractMethodTestBase)
    checkCodeHasNoErrors(EtaExpansionNumericWideningCode_SAMWithNonEmptyParams)
  }

  protected val EtaExpansionNumericWidening_NoSAM_Code: String =
    """def foo(): Int = 1
      |val a: () => Long = foo
      |val b: () => Long = foo _
      |""".stripMargin
  def testEtaExpansionNumericWidening_NoSAM(): Unit =
    checkCodeHasNoErrors(EtaExpansionNumericWidening_NoSAM_Code)

  protected val EtaExpansionImplicit_SAMWithNoParametersClauses_Code: String =
    """class A
      |class B
      |implicit def a2b(a: A): B = new B
      |
      |abstract class C0 { def foo: B }
      |
      |def foo0: A = new A
      |def foo00(): A = new A
      |
      |val a1: C0 = foo0
      |val a2: C0 = foo0 _
      |val a3: C0 = foo00
      |val a4: C0 = foo00 _
      |""".stripMargin
  def testEtaExpansionImplicit_SAMWithNoParametersClauses(): Unit =
    assertMatches(messages(EtaExpansionImplicit_SAMWithNoParametersClauses_Code)) {
      case Error("foo0","Expression of type A doesn't conform to expected type C0") ::
        Error("foo0 _","Expression of type () => A doesn't conform to expected type C0") ::
        Error("foo00","Expression of type A doesn't conform to expected type C0") ::
        Error("foo00 _","Expression of type () => A doesn't conform to expected type C0") :: Nil =>
    }

  protected val EtaExpansionImplicit_SAMWithEmptyParameters_Code: String =
    """class A
      |class B
      |implicit def a2b(a: A): B = new B
      |
      |abstract class C00 { def foo(): B }
      |
      |def foo0: A = new A
      |def foo00(): A = new A
      |
      |val a1: C00 = foo0
      |val a2: C00 = foo0 _
      |val a3: C00 = foo00
      |val a4: C00 = foo00 _
      |""".stripMargin
  def testEtaExpansionImplicit_SAMWithEmptyParameters(): Unit =
    assertMatches(messages(EtaExpansionImplicit_SAMWithEmptyParameters_Code)) {
      // NOTE: in scala 2.11 (with experimental flag) the compiler also produces an error for `val a4: C00 = foo00 _`
      // but looks like the root issue is bug https://github.com/scala/bug/issues/12333
      // (see comment for SingleAbstractMethodTestBase)
      case Error("foo0","Expression of type A doesn't conform to expected type C00") :: Nil =>
    }

  protected val EtaExpansionImplicit_SAMWithNonEmptyParameters_Code: String =
    """class A
      |class B
      |implicit def a2b(a: A): B = new B
      |
      |abstract class C1 { def foo(j: Int) : B}
      |
      |def foo1(i: Int): A = new A
      |
      |val u: C1 = foo1
      |val v: C1 = foo1 _
      |""".stripMargin
  def testEtaExpansionImplicit_SAMNonEmptyParameters(): Unit = {
    // NOTE: in scala 2.11 (with experimental flag) the compiler produces error for explicit eta-expansion (val v: C1 = foo1 _)
    // but looks like it is a bug https://github.com/scala/bug/issues/12333
    // (see comment for SingleAbstractMethodTestBase)
    checkCodeHasNoErrors(EtaExpansionImplicit_SAMWithNonEmptyParameters_Code)
  }

  protected val EtaExpansionToRunnableCode: String =
    """def f1: () => Unit = () => println()
      |def f2(): () => Unit = () => println()
      |def f3: Unit = println()
      |def f4(): Unit = println()
      |def f5(): Unit = println()
      |
      |val a1: Runnable = f1
      |val a2: Runnable = f2
      |val a3: Runnable = f3
      |val a4: Runnable = f4
      |val a5: Runnable = f5
      |// with _
      |val b1: Runnable = f1 _
      |val b2: Runnable = f2 _
      |val b3: Runnable = f3 _
      |val b4: Runnable = f4 _
      |val b5: Runnable = f5 _
      |// ... with parentheses
      |val c1: Runnable = f1()
      |val c2: Runnable = f2()
      |val c3: Runnable = f3()
      |val c4: Runnable = f4()
      |val c5: Runnable = f5()
      |""".stripMargin
  // Some random all-in-one added added in commit "a26fb59e Svyatoslav Ilinskiy <ilinskiy.sv@gmail.com> on 6/3/2016 at 12:58"
  // (I unified it between different Scala versions and added more examples)
  def testEtaExpansionToRunnableCode(): Unit =
    assertMatches(messages(EtaExpansionToRunnableCode)) {
      case Error("f1", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("f3", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //
        // NOTE: We don't show these errors but compiler does
        // (looks like due to the bug https://github.com/scala/bug/issues/12334)
        // (see comment for SingleAbstractMethodTestBase)
        //Error("f2", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        //Error("f4", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //Error("f5", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //
        Error("f1()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f2()", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("()", "f3 does not take parameters") ::
        Error("f4()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f5()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Nil =>
    }

  protected val EtaExpansionToScalaRunnableCode: String =
    s"""trait SRunnable { def run(): Unit }
       |
       |def f1: () => Unit = () => println()
       |def f2(): () => Unit = () => println()
       |def f3: Unit = println()
       |def f4(): Unit = println()
       |def f5(): Unit = println()
       |
       |val a1: SRunnable = f1
       |val a2: SRunnable = f2
       |val a3: SRunnable = f3
       |val a4: SRunnable = f4
       |val a5: SRunnable = f5
       |// with _
       |val b1: SRunnable = f1 _
       |val b2: SRunnable = f2 _
       |val b3: SRunnable = f3 _
       |val b4: SRunnable = f4 _
       |val b5: SRunnable = f5 _
       |// ... with parentheses
       |val c1: SRunnable = f1()
       |val c2: SRunnable = f2()
       |val c3: SRunnable = f3()
       |val c4: SRunnable = f4()
       |val c5: SRunnable = f5()
       |""".stripMargin
  def testEtaExpansionToScalaRunnableCode(): Unit =
    assertMatches(messages(EtaExpansionToScalaRunnableCode)) {
      case Error("f1", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("f3", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //
        // NOTE: We don't show these errors but compiler does
        // (looks like due to the bug https://github.com/scala/bug/issues/12334)
        // (see comment for SingleAbstractMethodTestBase)
        //Error("f2", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        //Error("f4", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //Error("f5", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //
        Error("f1()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f2()", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("()", "f3 does not take parameters") ::
        Error("f4()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f5()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Nil =>
    }

  protected val InvalidEtaExpansionToJavaFunctionCode =
    """def f11: Int => Unit = (x) => println()
      |def f12(): Int => Unit = (x) => println()
      |def f13(x: Int) = println()
      |def f14(x: Int): Unit = println()
      |
      |type JF[A, B] = java.util.function.Function[A, B]
      |val a1: JF[Int, Unit] = f11
      |val a2: JF[Int, Unit] = f12
      |val a3: JF[Int, Unit] = f13
      |val a4: JF[Int, Unit] = f14
      |
      |val b1: JF[Int, Unit] = f11 _
      |val b2: JF[Int, Unit] = f12 _
      |val b3: JF[Int, Unit] = f13 _
      |val b4: JF[Int, Unit] = f14 _
      |""".stripMargin
  def testInvalidEtaExpansionToJavaFunction(): Unit =
    assertMatches(messages(InvalidEtaExpansionToJavaFunctionCode)) {
      case Error("f11", doesNotConform()) ::
        Error("f12", doesNotConform()) ::
        //
        Error("f11 _", doesNotConform()) ::
        Error("f12 _", doesNotConform()) ::
        // NOTE: compiler generates these two extra errors for scala 2.11 (under experimental flag),
        // but it looks like a bug: https://github.com/scala/bug/issues/12333
        // (see comment for SingleAbstractMethodTestBase)
        //Error("f13 _", doesNotConform()) ::
        //Error("f14 _", doesNotConform()) ::
        Nil =>
    }

  protected val WildcardExtrapolationWithExistentialTypesCode =
    """class P[R]
      |
      |trait MyChangeListener[T] {
      |  def changed(observable: P[_ <: T])
      |}
      |
      |def l[T]: MyChangeListener[_ >: T] = (observable: P[_ <: T]) => ()
      |""".stripMargin
  def testWildcardExtrapolationWithExistentialTypes(): Unit =
    checkCodeHasNoErrors(WildcardExtrapolationWithExistentialTypesCode)

  def testWildcardExtrapolationWithParameterizedTypes(): Unit =
    checkCodeHasNoErrors(
      """trait MyObservableValue[T] {
        |  def addListener(listener: MyChangeListener[_ >: T])
        |}
        |
        |trait MyChangeListener[T] {
        |  def changed(observable: MyObservableValue[T])
        |}
        |
        |val observableValue: MyObservableValue[Int] = ???
        |observableValue.addListener((observable: MyObservableValue[Int]) => ())
        |""".stripMargin
    )

  protected val JavaStaticMethodsMixCode =
    """public class JavaMethods {
      |    public static String staticString0() { return null; }
      |    public static String staticString1(String s) { return null; }
      |    public static String staticString2(String s1, String s2) { return null; }
      |
      |    public static JavaMethods staticMyClass0() { return null; }
      |    public static JavaMethods staticMyClass1(String s) { return null; }
      |    public static JavaMethods staticMyClass2(String s1, String s2) { return null; }
      |}
      |""".stripMargin

  def testJavaMethodEtaExpansion_Explicit_NoExpectedTypes_NoSAM(): Unit =
    checkCodeHasNoErrors(
      """val f0 = JavaMethods.staticString0 _
        |val f1 = JavaMethods.staticString1 _
        |val f2 = JavaMethods.staticString2 _
        |
        |val f3 = JavaMethods.staticMyClass0 _
        |val f4 = JavaMethods.staticMyClass1 _
        |val f5 = JavaMethods.staticMyClass2 _
        |""".stripMargin,
      Some(JavaStaticMethodsMixCode)
    )

  def testJavaMethodEtaExpansion_Automatic_WithExpectedTypes_NoSAM(): Unit =
    checkCodeHasNoErrors(
      """val g0: () => String                    = JavaMethods.staticString0
        |val g1: String => String                = JavaMethods.staticString1
        |val g2: (String, String) => String      = JavaMethods.staticString2
        |
        |val g3: () => JavaMethods               = JavaMethods.staticMyClass0
        |val g4: String => JavaMethods           = JavaMethods.staticMyClass1
        |val g5: (String, String) => JavaMethods = JavaMethods.staticMyClass2
        |""".stripMargin,
      Some(JavaStaticMethodsMixCode)
    )

  def testJavaMethodEtaExpansion_Explicit_WithExpectedTypes_NoSAM(): Unit =
    checkCodeHasNoErrors(
      """val g0: () => String                    = JavaMethods.staticString0 _
        |val g1: String => String                = JavaMethods.staticString1 _
        |val g2: (String, String) => String      = JavaMethods.staticString2 _
        |
        |val g3: () => JavaMethods               = JavaMethods.staticMyClass0 _
        |val g4: String => JavaMethods           = JavaMethods.staticMyClass1 _
        |val g5: (String, String) => JavaMethods = JavaMethods.staticMyClass2 _
        |""".stripMargin,
      Some(JavaStaticMethodsMixCode)
    )

  def testNoJavaMethodEtaExpansion_JustMissingArguments(): Unit = {
    val code =
      """val g0 = JavaMethods.staticString0
        |val g1 = JavaMethods.staticString1
        |val g2 = JavaMethods.staticString2
        |
        |val g3 = JavaMethods.staticMyClass0
        |val g4 = JavaMethods.staticMyClass1
        |val g5 = JavaMethods.staticMyClass2
        |""".stripMargin
    assertMatches(messages(code, Some(JavaStaticMethodsMixCode))) {
      case Error("JavaMethods.staticString1", missingArguments()) ::
        Error("JavaMethods.staticString2", missingArguments()) ::
        Error("JavaMethods.staticMyClass1", missingArguments()) ::
        Error("JavaMethods.staticMyClass2", missingArguments()) :: Nil =>
    }
  }

  protected val ScalaStaticMethodsMixCode =
    """class ScalaMethods
      |object ScalaMethods {
      |  def scalaStaticString0: String = null
      |  def scalaStaticString00(): String = null
      |  def scalaStaticString1(s: String): String = null
      |  def scalaStaticString2(s1: String, s2: String): String = null
      |
      |  def scalaStaticMyClass0: ScalaMethods = null
      |  def scalaStaticMyClass00(): ScalaMethods = null
      |  def scalaStaticMyClass1(s: String): ScalaMethods = null
      |  def scalaStaticMyClass2(s1: String, s2: String): ScalaMethods = null
      |}""".stripMargin


  def testScalaMethodEtaExpansion_Explicit_NoExpectedTypes_NoSAM(): Unit =
    checkCodeHasNoErrors(
      s"""$ScalaStaticMethodsMixCode
         |val f1 = ScalaMethods.scalaStaticString0 _
         |val f2 = ScalaMethods.scalaStaticString00 _
         |val f3 = ScalaMethods.scalaStaticString1 _
         |val f4 = ScalaMethods.scalaStaticString2 _
         |
         |val f5 = ScalaMethods.scalaStaticMyClass0 _
         |val f6 = ScalaMethods.scalaStaticMyClass00 _
         |val f7 = ScalaMethods.scalaStaticMyClass1 _
         |val f8 = ScalaMethods.scalaStaticMyClass2 _
         |""".stripMargin
    )

  def testScalaMethodEtaExpansion_Automatic_WithExpectedTypes_NoSAM(): Unit = {
    val code =
      s"""$ScalaStaticMethodsMixCode
         |val f1: () => String                     = ScalaMethods.scalaStaticString0
         |val f2: () => String                     = ScalaMethods.scalaStaticString00
         |val f3: String => String                 = ScalaMethods.scalaStaticString1
         |val f4: (String, String) => String       = ScalaMethods.scalaStaticString2
         |val f5: () => ScalaMethods               = ScalaMethods.scalaStaticMyClass0
         |val f6: () => ScalaMethods               = ScalaMethods.scalaStaticMyClass00
         |val f7: String => ScalaMethods           = ScalaMethods.scalaStaticMyClass1
         |val f8: (String, String) => ScalaMethods = ScalaMethods.scalaStaticMyClass2
         |""".stripMargin
    assertMatches(messages(code)) {
      case Error("ScalaMethods.scalaStaticString0", "Expression of type String doesn't conform to expected type () => String") ::
        Error("ScalaMethods.scalaStaticMyClass0", "Expression of type ScalaMethods doesn't conform to expected type () => ScalaMethods") :: Nil =>
    }
  }

  def testScalaMethodEtaExpansion_Explicit_WithExpectedTypes_NoSAM(): Unit =
    checkCodeHasNoErrors(
      s"""$ScalaStaticMethodsMixCode
         |val f1: () => String                     = ScalaMethods.scalaStaticString0 _
         |val f2: () => String                     = ScalaMethods.scalaStaticString00 _
         |val f3: String => String                 = ScalaMethods.scalaStaticString1 _
         |val f4: (String, String) => String       = ScalaMethods.scalaStaticString2 _
         |
         |val f5: () => ScalaMethods               = ScalaMethods.scalaStaticMyClass0 _
         |val f6: () => ScalaMethods               = ScalaMethods.scalaStaticMyClass00 _
         |val f7: String => ScalaMethods           = ScalaMethods.scalaStaticMyClass1 _
         |val f8: (String, String) => ScalaMethods = ScalaMethods.scalaStaticMyClass2 _
         |""".stripMargin
    )

  def testNoScalaMethodEtaExpansion_JustMissingArguments(): Unit = {
    val code =
      s"""$ScalaStaticMethodsMixCode
         |val f1 = ScalaMethods.scalaStaticString0
         |val f2 = ScalaMethods.scalaStaticString00
         |val f3 = ScalaMethods.scalaStaticString1
         |val f4 = ScalaMethods.scalaStaticString2
         |
         |val f5 = ScalaMethods.scalaStaticMyClass0
         |val f6 = ScalaMethods.scalaStaticMyClass00
         |val f7 = ScalaMethods.scalaStaticMyClass1
         |val f8 = ScalaMethods.scalaStaticMyClass2
         |""".stripMargin
    assertMatches(messages(code, Some(JavaStaticMethodsMixCode))) {
      case Error("ScalaMethods.scalaStaticString1", missingArguments()) ::
        Error("ScalaMethods.scalaStaticString2", missingArguments()) ::
        Error("ScalaMethods.scalaStaticMyClass1", missingArguments()) ::
        Error("ScalaMethods.scalaStaticMyClass2", missingArguments()) :: Nil =>
    }
  }

  protected val JavaEtaExpansion_StaticMethodFromClassWithSAM_Mix_JavaCode =
    """public abstract class JavaAbstractClass {
      |    public abstract String abstractFoo();
      |
      |    public static String staticString0() { return null; }
      |    public static String staticString1(String s) { return null; }
      |    public static String staticString2(String s1, String s2) { return null; }
      |
      |    public static JavaAbstractClass staticMyClass0() { return null; }
      |    public static JavaAbstractClass staticMyClass1(String s) { return null; }
      |    public static JavaAbstractClass staticMyClass2(String s1, String s2) { return null; }
      |}
      |""".stripMargin
  protected val JavaEtaExpansion_StaticMethodFromClassWithSAM_Mix_Code =
    """def test: JavaAbstractClass = JavaAbstractClass.staticString0
      |def test: JavaAbstractClass = JavaAbstractClass.staticString1
      |def test: JavaAbstractClass = JavaAbstractClass.staticString2
      |
      |def test: JavaAbstractClass = JavaAbstractClass.staticMyClass0
      |def test: JavaAbstractClass = JavaAbstractClass.staticMyClass1
      |def test: JavaAbstractClass = JavaAbstractClass.staticMyClass2
      |""".stripMargin
  def testJavaEtaExpansion_StaticMethodFromClassWithSAM_Mix(): Unit = {
    val actual = messages(
      JavaEtaExpansion_StaticMethodFromClassWithSAM_Mix_Code, "worksheet.sc",
      Some(JavaEtaExpansion_StaticMethodFromClassWithSAM_Mix_JavaCode)
    )
    val actualMessagesText = messagesText(actual)

    val expectedMessagesText = cleanExpectedMessagesText(
      """Error(JavaAbstractClass.staticString1,Expression of type String => String doesn't conform to expected type JavaAbstractClass)
        |Error(JavaAbstractClass.staticString2,Expression of type (String, String) => String doesn't conform to expected type JavaAbstractClass)
        |
        |Error(JavaAbstractClass.staticMyClass0,Expression of type () => JavaAbstractClass doesn't conform to expected type JavaAbstractClass)
        |Error(JavaAbstractClass.staticMyClass1,Expression of type String => JavaAbstractClass doesn't conform to expected type JavaAbstractClass)
        |Error(JavaAbstractClass.staticMyClass2,Expression of type (String, String) => JavaAbstractClass doesn't conform to expected type JavaAbstractClass)""".stripMargin
    )

    assertEquals(expectedMessagesText, actualMessagesText)
  }


  // TODO: this
  def testScalaEtaExpansion_CompanionObjectMethodOfClassWithSAM_Mix(): Unit = {
    val scalaCode =
      """abstract class ScalaAbstractClass {
        |  def abstractFoo: String
        |}
        |
        |object ScalaAbstractClass {
        |  def scalaStaticFoo0: String = null
        |  def scalaStaticFoo00(): String = null
        |  def scalaStaticFoo1(s: String): String = null
        |  def scalaStaticFoo2(s1: String, s2: String): String = null
        |
        |  def scalaStaticMyClass0: ScalaAbstractClass = null
        |  def scalaStaticMyClass00(): ScalaAbstractClass = null
        |  def scalaStaticMyClass1(s: String): ScalaAbstractClass = null
        |  def scalaStaticMyClass2(s1: String, s2: String): ScalaAbstractClass = null
        |}
        |
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticFoo0
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticFoo00
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticFoo1
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticFoo2
        |
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticMyClass0
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticMyClass00
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticMyClass1
        |def test: ScalaAbstractClass = ScalaAbstractClass.scalaStaticMyClass2
        |""".stripMargin

    val actual = messages(scalaCode, "worksheet.sc", None)
    val actualMessagesText = messagesText(actual)

    val expectedMessagesText = cleanExpectedMessagesText(
      """Error(ScalaAbstractClass.scalaStaticFoo0,Expression of type String doesn't conform to expected type ScalaAbstractClass)
        |Error(ScalaAbstractClass.scalaStaticFoo00,Expression of type String doesn't conform to expected type ScalaAbstractClass)
        |Error(ScalaAbstractClass.scalaStaticFoo1,Missing arguments for method scalaStaticFoo1(String))
        |Error(ScalaAbstractClass.scalaStaticFoo2,Missing arguments for method scalaStaticFoo2(String, String))
        |
        |Error(ScalaAbstractClass.scalaStaticMyClass1,Missing arguments for method scalaStaticMyClass1(String))
        |Error(ScalaAbstractClass.scalaStaticMyClass2,Missing arguments for method scalaStaticMyClass2(String, String))
        |""".stripMargin
    )

    assertEquals(expectedMessagesText, actualMessagesText)
  }

  def testEtaExpansion_AllInOneMix(): Unit = {
    val javaCode =
      """public abstract class JavaAbstractClass {
        |    public static String staticFoo0() { return null; }
        |    public static String staticFoo1(String s) { return null; }
        |    public static String staticFoo2(String s1, String s2) { return null; }
        |
        |    public static JavaAbstractClass staticBar0() { return null; }
        |    public static JavaAbstractClass staticBar1(String s) { return null; }
        |    public static JavaAbstractClass staticBar2(String s1, String s2) { return null; }
        |}
        |""".stripMargin

    // NOTE: use worksheets to conveniently test how the code actually behaves
    // (Requires special mode when compile errors are printed right in the viewer and worksheet evaluation is not
    // stopped on errors. Hope I will merge it soon)
    val scalaCode =
      """scala.util.Properties.versionString
        |
        |//definitions
        |def m0 = "0"
        |def m00() = "00"
        |def m000(implicit s: String) = "000"
        |def m0000()(implicit s: String) = "0000"
        |def m1(x: String) = s"1: $x"
        |def m11(x: String)(implicit s: String) = s"11: $x"
        |def m111(x: String)()(implicit s: String) = s"111: $x"
        |def m2(x: String, y: String) = s"2: $x $y"
        |def m22(x: String, y: String)(implicit s: String) = s"22: $x $y"
        |def m222(x: String)(y: String)(implicit s: String) = s"222: $x $y}"
        |
        |implicit val s: String = "42"
        |
        |// section 1
        |val f0    = m0 _
        |val f00   = m00 _
        |val f000  = m000 _
        |val f0000 = m0000 _
        |val f1    = m1 _
        |val f11   = m11 _
        |val f111  = m111 _
        |val f2    = m2 _
        |val f22   = m22 _
        |val f222  = m222 _
        |
        |// section 2
        |val f0    = m0
        |val f00   = m00
        |val f000  = m000
        |val f0000 = m0000
        |val f1    = m1
        |val f11   = m11
        |val f111  = m111
        |val f2    = m2
        |val f22   = m22
        |val f222  = m222
        |
        |// section 3
        |val f0   : () => String               = m0
        |val f00  : () => String               = m00
        |val f000 : () => String               = m000
        |val f0000: () => String               = m0000
        |val f1   : String => String           = m1
        |val f11  : String => String           = m11
        |val f111 : String => String           = m111
        |val f2   : (String, String) => String = m2
        |val f22  : (String, String) => String = m22
        |val f222 : String => String => String = m222
        |
        |// section 4
        |val j01 = JavaAbstractClass.staticFoo0
        |val j11 = JavaAbstractClass.staticFoo1
        |val j21 = JavaAbstractClass.staticFoo2
        |
        |// section 5
        |val j02 = JavaAbstractClass.staticFoo0 _
        |val j12 = JavaAbstractClass.staticFoo1 _
        |val j22 = JavaAbstractClass.staticFoo2 _
        |
        |// section 6
        |val j03: () => JavaAbstractClass               = JavaAbstractClass.staticFoo0
        |val j13: String => JavaAbstractClass           = JavaAbstractClass.staticFoo1
        |val j23: (String, String) => JavaAbstractClass = JavaAbstractClass.staticFoo2
        |
        |def foo0(f: () => String) = "foo0: " + f()
        |def foo1(f: String => String) = "foo1: " + f("23")
        |def foo2(f: (String, String) => String) = "foo2: " + f("23", "42")
        |
        |// section 7
        |foo0(m0)
        |foo0(m00)
        |foo0(m000)
        |foo0(m0000)
        |foo0(JavaAbstractClass.staticFoo0)
        |
        |// section 8
        |foo0(m0 _)
        |foo0(m00 _)
        |foo0(m000 _)
        |foo0(m0000 _)
        |foo0(JavaAbstractClass.staticFoo0 _)
        |
        |// section 9
        |foo1(m1)
        |foo1(m11)
        |foo1(m111)
        |foo1(JavaAbstractClass.staticFoo1)
        |
        |// section 10
        |foo1(m1 _)
        |foo1(m11 _)
        |foo1(m111 _)
        |foo1(JavaAbstractClass.staticFoo1 _)
        |
        |// section 11
        |foo2(m2)
        |foo2(m22)
        |foo2(m222)
        |foo2(JavaAbstractClass.staticFoo2)
        |
        |// section 12
        |foo2(m2 _)
        |foo2(m22 _)
        |foo2(m222 _)
        |foo2(JavaAbstractClass.staticFoo2 _)
        |""".stripMargin

    val actual = messages(scalaCode, "worksheet.sc", Some(javaCode))
    val actualMessagesText = messagesText(actual)

    val expectedMessagesText = cleanExpectedMessagesText(
      """// section 2
        |Error(m1,Missing arguments for method m1(String))
        |Error(m11,Missing arguments for method m11(String)(String))
        |Error(m111,Missing arguments for method m111(String)()(String))
        |Error(m2,Missing arguments for method m2(String, String))
        |Error(m22,Missing arguments for method m22(String, String)(String))
        |Error(m222,Missing arguments for method m222(String)(String)(String))
        |// section 3
        |Error(m0,Expression of type String doesn't conform to expected type () => String)
        |Error(m000,Expression of type String doesn't conform to expected type () => String)
        |Error(m111,Expression of type String => () => String doesn't conform to expected type String => String)
        |// section 4
        |Error(JavaAbstractClass.staticFoo1,Missing arguments for method staticFoo1(String))
        |Error(JavaAbstractClass.staticFoo2,Missing arguments for method staticFoo2(String, String))
        |// section 6
        |Error(JavaAbstractClass.staticFoo0,Expression of type () => String doesn't conform to expected type () => JavaAbstractClass)
        |Error(JavaAbstractClass.staticFoo1,Expression of type String => String doesn't conform to expected type String => JavaAbstractClass)
        |Error(JavaAbstractClass.staticFoo2,Expression of type (String, String) => String doesn't conform to expected type (String, String) => JavaAbstractClass)
        |// section 7
        |Error(m0,Type mismatch, expected: () => String, actual: String)
        |Error(m000,Type mismatch, expected: () => String, actual: String)
        |// section 8
        |Error(m000 _,Type mismatch, expected: () => String, actual: String)
        |// section 9
        |Error(m111,Type mismatch, expected: String => String, actual: String => () => String)
        |// section 10
        |Error(m111 _,Type mismatch, expected: String => String, actual: String => () => String)
        |// section 11
        |Error(m222,Type mismatch, expected: (String, String) => String, actual: String => String => String)
        |// section 12
        |Error(m222 _,Type mismatch, expected: (String, String) => String, actual: String => String => String)
        |""".stripMargin
    )

    assertEquals(expectedMessagesText, actualMessagesText)
  }

  def testClassWithOverriddenAbstract(): Unit = {
    val ClassWithOverriddenAbstractJavaCode =
      """public class Abstracts {
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
        |""".stripMargin
    val ClassWithOverriddenAbstractCode =
      """object Test {
        |  def foo(x: Abstracts.Base) = ???
        |  def bar(x: Abstracts.Derived) = ???
        |
        |  foo(_ => ())
        |  bar(_ => ())
        |}
        |""".stripMargin
    checkCodeHasNoErrors(ClassWithOverriddenAbstractCode, Some(ClassWithOverriddenAbstractJavaCode))
  }

  def testBasicGenerics(): Unit = {
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

  def testTypeInference(): Unit = {
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

  def testFunctionNegOne(): Unit = {
    val code =
      """
        |def z(): Unit = println()
        |val y: Runnable = z()
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z()", doesNotConform()) :: Nil =>
    }
  }

  def testFunctionNegTwo(): Unit = {
    val code =
      """
        |def z: Unit = println()
        |val y: Runnable = z
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("z", doesNotConform()) :: Nil =>
    }
  }

  def testFunctionNegThree(): Unit = {
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

  def testUnderscoreOne(): Unit = {
    val code =
      """
        |trait Foo { def bar(i: Int, s: String): String }
        |val f: Foo = _ + _
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testUnderscoreTwo(): Unit = {
    val code =
      """
        |trait Foo { def bar(s: String): String }
        |val i: Foo = _.charAt(0).toString
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  def testSimpleNeg(): Unit = {
    val code =
      """
        |trait Foo { def blargle(i: Int): Unit }
        |val f: Foo = s => println(s.charAt(0))
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("charAt", cannotResolveSymbol()) :: Nil =>
    }
  }

  def testSimpleNegWrongReturnType(): Unit = {
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

  def testSimpleNegWrongParamNumber(): Unit = {
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

  def testSimpleNegWrongParamType(): Unit = {
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

  protected val SimpleNegRightParamWrongReturnCode =
    """object T {
      |  trait Blergh { def apply(i: Int): String }
      |  (j => j): Blergh
      |}
      |""".stripMargin
  def testSimpleNegRightParamWrongReturn(): Unit =
    assertMatches(messages(SimpleNegRightParamWrongReturnCode)) {
      case Error("Blergh", cannotUpcast()) :: Nil =>
    }

  def testConstructorWithArgs(): Unit = {
    val code =
      """
        |abstract class Foo(s: String) { def a(): String }
        |val f: Foo = () => ""
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("() => \"\"", doesNotConform()) :: Nil =>
    }
  }

  protected val ImplicitConversionWithSAMCode =
    """import scala.language.implicitConversions
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
      |""".stripMargin
  def testImplicitConversionWithSAM(): Unit =
    assertMatches(messages(ImplicitConversionWithSAMCode)) {
      case Error("() => 3", doesNotConform()) :: Nil =>
    }

  protected val UnimplementedWithSAMCode =
    """abstract class Foo { def a(): String }
      |val f: Foo = () => ???
      |""".stripMargin
  def testUnimplementedWithSAM(): Unit =
    checkCodeHasNoErrors(UnimplementedWithSAMCode)

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
      case Error("String", typeMismatch()) :: Nil =>
    }
  }

  protected val SimpleThreadRunnableCode =
    """new Thread(() => println()).run()"""
  def testSimpleThreadRunnable(): Unit =
    checkCodeHasNoErrors(SimpleThreadRunnableCode)

  protected val ValueDiscardingCode =
    """def goo(r: Runnable) = 2
      |goo(() => {1 + 1})
      |""".stripMargin
  def testValueDiscarding(): Unit =
    checkCodeHasNoErrors(ValueDiscardingCode)

  def testJavaGenerics(): Unit = {
    val code =
      """
        |import java.util.concurrent.FutureTask
        |
        |new FutureTask[String](() => "hi")
      """.stripMargin
    checkCodeHasNoErrors(code)
  }

  protected val SAMMethodReferenceCode =
    """trait F[T, R] {
      |  def apply(a: T): R
      |}
      |
      |def len(s: String): Int  = s.length
      |
      |val f: F[String, Int] = len
      |""".stripMargin
  def testSAMMethodReference(): Unit =
    checkCodeHasNoErrors(SAMMethodReferenceCode)

  val ExistentialBoundsCode =
    """trait Blargle[T] {
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
      |""".stripMargin
  def testExistentialBounds(): Unit =
    checkCodeHasNoErrors(ExistentialBoundsCode)

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

  protected val OverrideImplementSAMCode =
    """val s: Bar = () => 2
      |
      |abstract class Foo {
      |  def foo(): Int
      |}
      |
      |abstract class Bar extends Foo
      |""".stripMargin
  def testOverrideImplementSAM(): Unit =
    checkCodeHasNoErrors(OverrideImplementSAMCode)

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

  // SCL-18195
  def testEtaExpansionOfByNameParameters(): Unit =
    checkCodeHasNoErrors(
      """trait Foo {
        |  def foo(): Int
        |}
        |def method: Int = 1
        |def bar(): Foo = method _
        |def bar(block: => Int): Foo = block _
        |""".stripMargin
    )
}

abstract class SingleAbstractMethodTest_Since_2_12 extends SingleAbstractMethodTest_Since_2_11_experimental {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  // `val v: C00 = foo0 _` is OK since 2.12
  // `val v: C00 = foo00 _` is still OK in 2.13
  //
  // NOTE: `val a4: C00 = foo00 _` is highlighted as error in scala 2.12, but probably due to the compiler bug
  // see comment https://github.com/scala/bug/issues/12333#issuecomment-776615211
  // see comment for SingleAbstractMethodTestBase
  override def testEtaExpansionImplicit_SAMWithEmptyParameters(): Unit =
    assertMatches(messages(EtaExpansionImplicit_SAMWithEmptyParameters_Code)) {
      case Error("foo0","Expression of type A doesn't conform to expected type C00") ::
        //Error("foo0 _","Expression of type () => A doesn't conform to expected type C00") ::
        Error("foo00","Expression of type A doesn't conform to expected type C00") ::
        //Error("foo00 _","Expression of type () => A doesn't conform to expected type C00") :: =>
        Nil =>
    }

  override def testWildcardExtrapolationWithExistentialTypes(): Unit =
    assertMatches(messages(WildcardExtrapolationWithExistentialTypesCode)) {
      case Error("P[_ <: T]", typeMismatch()) :: Nil =>
    }

  override def testEtaExpansionToRunnableCode(): Unit =
    assertMatches(messages(EtaExpansionToRunnableCode)) {
      case Error("f1", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("f2", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("f3", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f4", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f5", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //
        // NOTE: We don't show these errors but compiler does
        // (looks like due to the bug https://github.com/scala/bug/issues/12334)
        // (see comment for SingleAbstractMethodTestBase)
        //Error("f2", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        //Error("f4", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //Error("f5", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //
        Error("f1()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f2()", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("()", "f3 does not take parameters") ::
        Error("f4()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f5()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Nil =>
    }

  override def testEtaExpansionToScalaRunnableCode(): Unit =
    assertMatches(messages(EtaExpansionToScalaRunnableCode)) {
      case Error("f1", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("f2", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("f3", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f4", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f5", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //
        // NOTE: We don't show these errors but compiler does
        // (looks like due to the bug https://github.com/scala/bug/issues/12334)
        // (see comment for SingleAbstractMethodTestBase)
        //Error("f2", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        //Error("f4", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //Error("f5", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //
        Error("f1()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f2()", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("()", "f3 does not take parameters") ::
        Error("f4()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f5()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Nil =>
    }

  // NOTE: `val a4: C00 = foo00 _` is highlighted as error in scala 2.12, but probably due to the compiler bug
  // see comment https://github.com/scala/bug/issues/12333#issuecomment-776615211
  // see comment for SingleAbstractMethodTestBase
  override def testEtaExpansionNumericWidening_SAMWithEmptyParams(): Unit =
    assertMatches(messages(EtaExpansionNumericWideningCode_SAMWithEmptyParams)) {
      case Error("foo00", "Expression of type Int doesn't conform to expected type C00") ::
        //Error("foo00 _", "Expression of type () => Int doesn't conform to expected type C00") ::
        Nil =>
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

  def testSCL17054(): Unit = checkCodeHasNoErrors(
    """
      |class Action[T]
      |trait Callable[T] {
      |  def call(): T
      |}
      |
      |def nonBlocking[T](task: Callable[T]): Action[T] =
      |  new Action[T]
      |
      |val action = nonBlocking(() => 123) // should be Action[String]
      |
      |def foo[T](action: Action[T]) = ???
      |
      |foo[Int](action)
      |""".stripMargin
  )

  def testSCL17170(): Unit = checkCodeHasNoErrors(
    """
      |sealed trait T
      |object T {
      |  case object A extends T
      |  case object B extends T
      |}
      |trait Action {
      |  def run(file: String, list: Seq[Int]): Unit
      |}
      |val x: Map[T, Action] = Map(
      |  T.A -> ((_, _) => println(42)),
      |  T.B -> ((_, _) => println(23))
      |)
      |class X
      |val y: Map[X, Action] = Map(
      |  new X -> ((_, _) => println(42)),
      |  new X -> ((_, _) => println(23))
      |)
      |""".stripMargin
  )

  def testSCL16596(): Unit = checkCodeHasNoErrors(
    """
      |trait Callback {
      |  def foo(): Unit
      |}
      |def method1(c: Callback): Unit = ()
      |def method2(c: Option[Callback]): Unit = ()
      |method1(() => ())
      |method2(Some(() => ()))
      |""".stripMargin
  )

  def testSCL11156(): Unit = checkCodeHasNoErrors(
    """
      |trait Parser[T] extends Function[String, Option[(T, String)]]
      |
      |object Parser {
      |  val item: Parser[Char] = {
      |    case "" => None
      |    case v => Some((v.charAt(0), v.substring(1)))
      |  }
      |}
      |
      |object Test extends App {
      |  println(Parser.item apply "abcdef")
      |}
      |""".stripMargin
  )

  override def testJavaEtaExpansion_StaticMethodFromClassWithSAM_Mix(): Unit = {
    val actual = messages(
      JavaEtaExpansion_StaticMethodFromClassWithSAM_Mix_Code, "worksheet.sc",
      Some(JavaEtaExpansion_StaticMethodFromClassWithSAM_Mix_JavaCode)
    )
    val actualMessagesText = messagesText(actual)

    val expectedMessagesText = cleanExpectedMessagesText(
      """Error(JavaAbstractClass.staticString0,Expression of type String doesn't conform to expected type JavaAbstractClass)
        |Error(JavaAbstractClass.staticString1,Expression of type String => String doesn't conform to expected type JavaAbstractClass)
        |Error(JavaAbstractClass.staticString2,Expression of type (String, String) => String doesn't conform to expected type JavaAbstractClass)
        |
        |Error(JavaAbstractClass.staticMyClass1,Expression of type String => JavaAbstractClass doesn't conform to expected type JavaAbstractClass)
        |Error(JavaAbstractClass.staticMyClass2,Expression of type (String, String) => JavaAbstractClass doesn't conform to expected type JavaAbstractClass)""".stripMargin
    )

    assertEquals(expectedMessagesText, actualMessagesText)
  }

  def testEtaExpansion_Mix(): Unit = {
    val javaCode =
      """public class JSamWrapper {
        |    public abstract static class JSam0 { public abstract String getType0(); }
        |    public abstract static class JSam1 { public abstract String getType1(int x); }
        |
        |    public static String jGetString0() { return "0"; }
        |    public static String jGetString1(int x) { return "1"; }
        |}
        |""".stripMargin
    val scalaCode =
      """abstract class Sam0 { def method0: String }
        |abstract class Sam00 { def method00(): String }
        |abstract class Sam1 { def method1(x: Int): String }
        |
        |def foo0: String = null
        |def foo00(): String = null
        |def foo1(x: Int): String = null
        |
        |def f: Sam0  = foo0
        |def f: Sam0  = foo0 _
        |def f: Sam0  = foo00
        |def f: Sam0  = foo00 _
        |def f: Sam00  = foo0
        |def f: Sam00  = foo0 _
        |def f: Sam00  = foo00
        |def f: Sam00  = foo00 _
        |
        |def f: () => String = foo0
        |def f: () => String = foo0 _
        |def f: () => String = foo00
        |def f: () => String = foo00 _
        |
        |def f: Sam1  = foo1
        |def f: Sam1  = foo1 _
        |def f: (Int) => String = foo1
        |def f: (Int) => String = foo1 _
        |
        |import JSamWrapper._
        |
        |def f: JSam0  = foo0
        |def f: JSam0  = foo0 _
        |def f: JSam1  = foo1
        |def f: JSam1  = foo1 _
        |
        |def f: JSam0  = jGetString0
        |def f: JSam0  = jGetString0 _
        |def f: JSam1  = jGetString1
        |def f: JSam1  = jGetString1 _
        |
        |def f: JSam0  = () => "42"
        |def f: JSam1  = x => "42"
        |""".stripMargin

    val actual = messages(scalaCode, "worksheet.sc", Some(javaCode))
    val actualMessagesText = messagesText(actual)

    val expectedMessagesText = cleanExpectedMessagesText(
      """Error(foo0,Expression of type String doesn't conform to expected type Sam0)
        |Error(foo0 _,Expression of type () => String doesn't conform to expected type Sam0)
        |Error(foo00,Expression of type String doesn't conform to expected type Sam0)
        |Error(foo00 _,Expression of type () => String doesn't conform to expected type Sam0)
        |Error(foo0,Expression of type String doesn't conform to expected type Sam00)
        |Error(foo00,Expression of type String doesn't conform to expected type Sam00)
        |Error(foo0,Expression of type String doesn't conform to expected type () => String)
        |Error(foo0,Expression of type String doesn't conform to expected type JSam0)
        |Error(jGetString0,Expression of type String doesn't conform to expected type JSam0)
        |""".stripMargin
    )

    assertEquals(expectedMessagesText, actualMessagesText)
  }
}

abstract class SingleAbstractMethodTest_Since_2_13 extends SingleAbstractMethodTest_Since_2_12 {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  override def testEtaExpansionToRunnableCode(): Unit =
    assertMatches(messages(EtaExpansionToRunnableCode)) {
      case Error("f1", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("f2", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("f3", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f4", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f5", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //
        // NOTE: yes, b1-b5 are valid in 2.13 (at least in 2.13.4)
//        Error("f1", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
//        Error("f2", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
//        Error("f3", "Expression of type Unit doesn't conform to expected type Runnable") ::
//        Error("f4", "Expression of type Unit doesn't conform to expected type Runnable") ::
//        Error("f5", "Expression of type Unit doesn't conform to expected type Runnable") ::
        //
        Error("f1()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f2()", "Expression of type () => Unit doesn't conform to expected type Runnable") ::
        Error("()", "f3 does not take parameters") ::
        Error("f4()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Error("f5()", "Expression of type Unit doesn't conform to expected type Runnable") ::
        Nil =>
    }

  override def testEtaExpansionToScalaRunnableCode(): Unit =
    assertMatches(messages(EtaExpansionToScalaRunnableCode)) {
      case Error("f1", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("f2", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("f3", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f4", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f5", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //
        // NOTE: yes, b1-b5 are valid in 2.13 (at least in 2.13.4)
//        Error("f1", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
//        Error("f2", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
//        Error("f3", "Expression of type Unit doesn't conform to expected type SRunnable") ::
//        Error("f4", "Expression of type Unit doesn't conform to expected type SRunnable") ::
//        Error("f5", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        //
        Error("f1()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f2()", "Expression of type () => Unit doesn't conform to expected type SRunnable") ::
        Error("()", "f3 does not take parameters") ::
        Error("f4()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Error("f5()", "Expression of type Unit doesn't conform to expected type SRunnable") ::
        Nil =>
    }
}

class SingleAbstractMethodTest_2_13 extends SingleAbstractMethodTest_Since_2_13 {
  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

class SingleAbstractMethodTest_2_12 extends SingleAbstractMethodTest_Since_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

// TODO: add tests for 2.11 without experimental flag
class SingleAbstractMethodTest_2_11_experimental extends SingleAbstractMethodTest_Since_2_11_experimental {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11

  protected override def setUp(): Unit = {
    super.setUp()
    enableExperimentalFeatures()
  }

  private def enableExperimentalFeatures(): Unit = {
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(myFixture.getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      experimental = true
    )
    defaultProfile.setSettings(newSettings)
  }
}