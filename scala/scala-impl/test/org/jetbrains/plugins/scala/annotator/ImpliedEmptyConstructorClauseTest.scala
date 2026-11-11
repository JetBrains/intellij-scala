package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * A constructor must have a non-implicit parameter clause, otherwise the compiler adds an empty clause
 * (`NamerOps.normalizeIfConstructor`): in front of a leading `implicit` clause, or at the end if all clauses
 * are `using` clauses (including the evidence of context bounds). The expected results are the compiler's.
 */
abstract class ImpliedEmptyConstructorClauseTestBase extends ScalaHighlightingTestBase {

  protected val Declarations: String =
    """trait Show[A]
      |given Show[Int] = new Show[Int] {}
      |given Int = 1
      |given Long = 2L
      |val s: Show[Int] = summon[Show[Int]]
      |
      |class U(using y: Int)
      |class I(implicit y: Int)
      |class C4[A: Show]
      |class M(using y: Int)(using z: Long)
      |""".stripMargin

  // `U(using y: Int)()`, `M(using y: Int)(using z: Long)()`
  def testUsingOnlyConstructors(): Unit = assertNoErrors(
    Declarations +
      """val a1 = new U
        |val a2 = new U()
        |val a3 = new U(using 1)
        |val a4 = new U(using 1)()
        |val d1 = new M
        |val d2 = new M(using 1)(using 2L)
        |val d3 = new M(using 1)(using 2L)()
        |val d4 = new M(using 1)
        |val d5 = new M(using 1)()
        |val g1 = U(using 1)
        |val g2 = U()
        |""".stripMargin
  )

  // `I()(implicit y: Int)`
  def testLeadingImplicitConstructor(): Unit = assertErrors(
    Declarations +
      """val b1 = new I
        |val b2 = new I()
        |val b3 = new I()(1)
        |val b4 = new I(1)
        |val b5 = new I(using 1)
        |val b6 = new I()(using 1)
        |val g5 = I()
        |val g6 = I(using 1)
        |""".stripMargin,
    Error("(1", "Too many arguments for constructor I(Int)")
  )

  // Leading `using` clauses: the compiler infers the type arguments from the expected type before it searches
  // for their arguments, if only empty and `using` clauses follow them, e.g. `new B` is `new B[Int](using given_Int)()`
  def testLeadingUsingClausesWithTypeArgumentsFromExpectedType(): Unit = assertErrors(
    """trait Eq[A]
      |given Eq[Int] = new Eq[Int] {}
      |given Eq[String] = new Eq[String] {}
      |given Int = 1
      |given List[Int] = Nil
      |
      |class B[A](using a: A)
      |class B2[A](using a: A)(using b: List[A])
      |class B3[A](using a: A)()
      |class B4[A](using a: A)()(using b: List[A])
      |class B5[A](using a: A)(x: Int)
      |class B6[A](using a: A)()(x: Int)
      |class E[A: Eq]
      |class E2[A](using e: Eq[A])
      |
      |val b1: B[Int] = new B
      |val b2: B[Int] = new B()
      |val b3: B[Int] = B()
      |val b4: B2[Int] = new B2
      |val b5: B3[Int] = new B3
      |val b6: B3[Int] = new B3()
      |val b7: B4[Int] = new B4
      |val b8: B4[Int] = new B4()
      |val e1: E[Int] = new E
      |val e2: E[Int] = new E()
      |val e3: E[Int] = E()
      |val e4: E2[Int] = new E2
      |val f1: B5[Int] = new B5(1)
      |val f2: B6[Int] = new B6()(1)
      |""".stripMargin,
    Error("B5(1)", "No implicit search was attempted, the expected type is not specific enough: A"),
    Error("B6()(1)", "No implicit search was attempted, the expected type is not specific enough: A"),
  )
}

/** Before Scala 3.6, evidence is `implicit`: `C4[A]()(implicit ev: Show[A])` */
class ImpliedEmptyConstructorClauseTest_3_3 extends ImpliedEmptyConstructorClauseTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  def testContextBound(): Unit = assertErrors(
    Declarations +
      """val c1 = new C4[Int]
        |val c2 = new C4[Int]()
        |val c3 = new C4[Int](using s)
        |val c4 = new C4[Int](using s)()
        |val c5 = new C4[Int]()(using s)
        |val c6 = new C4[Int]()(s)
        |val g3 = C4[Int](using s)
        |val g4 = C4[Int]()
        |""".stripMargin,
    Error("(using s)()", "Unspecified value parameters: show$A$0: Show[Int]"),
    Error(" s", "Too many arguments for constructor C4"),
  )
}

/** Since Scala 3.6, evidence is `using`: `C4[A](using ev: Show[A])()` */
class ImpliedEmptyConstructorClauseTest_3_9 extends ImpliedEmptyConstructorClauseTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  def testContextBound(): Unit = assertNoErrors(
    Declarations +
      """val c1 = new C4[Int]
        |val c2 = new C4[Int]()
        |val c3 = new C4[Int](using s)
        |val c4 = new C4[Int](using s)()
        |val g3 = C4[Int](using s)
        |val g4 = C4[Int]()
        |""".stripMargin
  )
}

/** The same with all highlighting passes */
class ImpliedEmptyConstructorClauseHighlightingTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  def testValidInvocations(): Unit = checkTextHasNoErrors(
    """trait Show[A]
      |given Show[Int] = new Show[Int] {}
      |given Int = 1
      |given Long = 2L
      |val s: Show[Int] = summon[Show[Int]]
      |
      |class U(using y: Int)
      |class I(implicit y: Int)
      |class C4[A: Show]
      |class M(using y: Int)(using z: Long)
      |
      |val a = Seq(new U, new U(), new U(using 1), new U(using 1)(), U(using 1), U())
      |val b = Seq(new I, new I(), new I()(1), new I(using 1), new I()(using 1), I(), I(using 1))
      |val c = Seq(new C4[Int], new C4[Int](), new C4[Int](using s), new C4[Int](using s)(), C4[Int](using s), C4[Int]())
      |val d = Seq(new M, new M(using 1)(using 2L), new M(using 1)(using 2L)(), new M(using 1), new M(using 1)())
      |""".stripMargin
  )
}

/**
 * The types of universal applies. As for a constructor invocation, the compiler applies the implied empty clause
 * if no arguments are given for it, e.g. `G[Int]` is `new G[Int](given_Show_Int)()`.
 */
abstract class ImpliedEmptyConstructorClauseTypeTestBase extends TypeInferenceTestBase {

  protected def checkType(expression: String, expectedType: String): Unit = doTest(
    s"""trait Show[A]
       |given Show[Int] = new Show[Int] {}
       |given Int = 1
       |given Long = 2L
       |val s: Show[Int] = summon[Show[Int]]
       |
       |class U(using y: Int)
       |class I(implicit y: Int)
       |class C4[A: Show]
       |class M(using y: Int)(using z: Long)
       |class G[A](using y: Show[A]):
       |  def g: Int = 0
       |
       |val x = $START$expression$END
       |//$expectedType
       |""".stripMargin
  )

  def testUsingOnly(): Unit = checkType("U(using 1)", "U")

  def testUsingOnlyWithEmptyArguments(): Unit = checkType("U()", "U")

  def testUsingOnlyWithTypeArguments(): Unit = checkType("G[Int]", "G[Int]")

  def testUsingOnlyWithTypeArgumentsMember(): Unit = checkType("G[Int].g", "Int")

  def testUsingOnlyWithTypeAndUsingArguments(): Unit = checkType("G[Int](using s)", "G[Int]")

  def testUsingOnlyWithTypeAndEmptyArguments(): Unit = checkType("G[Int]()", "G[Int]")

  def testUsingOnlyClauses(): Unit = checkType("M(using 1)", "M")

  def testUsingOnlyClausesWithAllArguments(): Unit = checkType("M(using 1)(using 2L)", "M")

  def testUsingOnlyClausesWithEmptyArguments(): Unit = checkType("M()", "M")

  def testLeadingImplicit(): Unit = checkType("I()", "I")

  // `I()(using 1)`
  def testLeadingImplicitWithUsingArguments(): Unit = checkType("I(using 1)", "I")
}

/** Before Scala 3.6, evidence is `implicit`: `C4[A]()(implicit ev: Show[A])` */
class ImpliedEmptyConstructorClauseTypeTest_3_3 extends ImpliedEmptyConstructorClauseTypeTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  def testContextBound(): Unit = checkType("C4[Int]", "C4[Int]")

  def testContextBoundWithEmptyArguments(): Unit = checkType("C4[Int]()", "C4[Int]")

  // `C4[Int]()(using s)`
  def testContextBoundWithUsingArguments(): Unit = checkType("C4[Int](using s)", "C4[Int]")
}

/** Since Scala 3.6, evidence is `using`: `C4[A](using ev: Show[A])()` */
class ImpliedEmptyConstructorClauseTypeTest_3_9 extends ImpliedEmptyConstructorClauseTypeTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  def testContextBound(): Unit = checkType("C4[Int]", "C4[Int]")

  def testContextBoundWithEmptyArguments(): Unit = checkType("C4[Int]()", "C4[Int]")

  def testContextBoundWithUsingArguments(): Unit = checkType("C4[Int](using s)", "C4[Int]")
}

/** Known differences with the compiler */
class ImpliedEmptyConstructorClauseFailingTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def shouldPass = false

  // Arguments after the implied empty clause: the compiler reports that the constructor does not take more parameters.
  // Excess argument clauses aren't reported for constructors in general, e.g. `class E(); new E()()`.

  def testUsingOnlyConstructorExcessArguments(): Unit = checkHasErrorAroundCaret(
    s"""given Int = 1
       |class U(using y: Int)
       |val a5 = new U()(using ${CARET}1)
       |""".stripMargin
  )

  def testContextBoundExcessArguments(): Unit = checkHasErrorAroundCaret(
    s"""trait Show[A]
       |given Show[Int] = new Show[Int] {}
       |val s: Show[Int] = summon[Show[Int]]
       |class C4[A: Show]
       |val c5 = new C4[Int]()(using ${CARET}s)
       |""".stripMargin
  )
}
