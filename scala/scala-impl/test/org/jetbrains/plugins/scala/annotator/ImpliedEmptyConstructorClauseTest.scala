package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
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
        |""".stripMargin,
    Error("(1", "Too many arguments for constructor I(Int)")
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
      |val b = Seq(new I, new I(), new I()(1), new I(using 1), new I()(using 1), I())
      |val c = Seq(new C4[Int], new C4[Int](), new C4[Int](using s), new C4[Int](using s)(), C4[Int](using s), C4[Int]())
      |val d = Seq(new M, new M(using 1)(using 2L), new M(using 1)(using 2L)(), new M(using 1), new M(using 1)())
      |""".stripMargin
  )
}

/** Known differences with the compiler */
class ImpliedEmptyConstructorClauseFailingTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def shouldPass = false

  // the compiler applies `I()(using 1)` to the universal apply
  def testUniversalApplyWithUsingArgumentsOfLeadingImplicitConstructor(): Unit = checkTextHasNoErrors(
    """class I(implicit y: Int)
      |val g6 = I(using 1)
      |""".stripMargin
  )

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
