package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

/**
 * Intersections and unions are the same type if each is a subtype of the other (`TypeComparer.isSameType`),
 * which matters where their equivalence is required, e.g. for an invariant type argument.
 * The inferred types are those of Scala 3.3.7 and 3.8.3.
 */
class Scala3AndOrTypesEquivalenceTest extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private val definitions =
    """trait Bar
      |trait IO
      |trait Sub extends IO
      |trait Inv[A]
      |""".stripMargin

  private def assertEquivalent(from: String, to: String): Unit = assertNoErrors(
    s"""$definitions
       |val from: Inv[$from] = ???
       |val to: Inv[$to] = from
       |""".stripMargin
  )

  private def assertNotEquivalent(from: String, to: String): Unit = assertErrors(
    s"""$definitions
       |val from: Inv[$from] = ???
       |val to: Inv[$to] = from
       |""".stripMargin,
    Error("from", s"Expression of type Inv[$from] doesn't conform to expected type Inv[$to]")
  )

  private def doDefinitionTest(definition: String, expected: String): Unit = doTest(
    s"""$definitions
       |$definition
       |val selected = ${START}x$END
       |//$expected
       |""".stripMargin
  )

  def testIntersectionInTypeArgumentOfCall(): Unit = assertNoErrors(
    s"""$definitions
       |trait <[A, B]
       |def foo[S, B, S2]: B < (S & S2) = ???
       |def bar(x: String < (IO & Bar)): Unit = ()
       |
       |bar(foo[IO, String, IO & Bar])
       |""".stripMargin
  )

  def testIntersectionWithDuplicateOperand(): Unit = assertEquivalent("IO & (IO & Bar)", "IO & Bar")

  def testIntersectionWithSwappedOperands(): Unit = assertEquivalent("IO & Bar", "Bar & IO")

  def testIntersectionAssociativity(): Unit = assertEquivalent("(IO & Bar) & String", "IO & (Bar & String)")

  def testIntersectionWithSupertypeOperand(): Unit = assertEquivalent("Sub & IO", "Sub")

  def testTypeAsIntersectionWithSupertypeOperand(): Unit = assertEquivalent("Sub", "Sub & IO")

  def testUnionWithDuplicateOperand(): Unit = assertEquivalent("IO | (IO | Bar)", "IO | Bar")

  def testUnionWithSwappedOperands(): Unit = assertEquivalent("IO | Bar", "Bar | IO")

  def testUnionWithSubtypeOperand(): Unit = assertEquivalent("Sub | IO", "IO")

  def testTypeAsUnionWithSubtypeOperand(): Unit = assertEquivalent("IO", "Sub | IO")

  def testIntersectionIsNotOperand(): Unit = assertNotEquivalent("IO & Bar", "IO")

  def testUnionIsNotOperand(): Unit = assertNotEquivalent("IO | Bar", "IO")

  def testTypeVariableInIntersection(): Unit = doDefinitionTest(
    """def f[T](x: Inv[T & IO]): T = ???
      |val x = f(??? : Inv[IO & Bar])
      |""".stripMargin,
    "IO & Bar"
  )

  def testTypeVariableInIntersectionWithDuplicateOperand(): Unit = doDefinitionTest(
    """def f[T](x: Inv[T & IO]): T = ???
      |val x = f(??? : Inv[IO & (IO & Bar)])
      |""".stripMargin,
    "IO & Bar"
  )

  def testTypeVariableInUnion(): Unit = doDefinitionTest(
    """def g[T](x: Inv[T | IO]): T = ???
      |val x = g(??? : Inv[IO | Bar])
      |""".stripMargin,
    "Bar"
  )

  def testTypeVariableInUnionWithDuplicateOperand(): Unit = doDefinitionTest(
    """def g[T](x: Inv[T | IO]): T = ???
      |val x = g(??? : Inv[IO | (IO | Bar)])
      |""".stripMargin,
    "Bar"
  )
}
