package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * The intersections and unions in the inferred types of definitions are simplified (`TypeOps.simplify`),
 * with the types inferred by Scala 3.3.7, 3.4.1, 3.5.2, 3.8.3 and 3.10.0-RC1.
 */
abstract class Scala3AndOrTypesSimplificationTestBase extends TypeInferenceTestBase {
  // Since Scala 3.5, the compiler simplifies all unions, not only the ones with a bottom operand
  private def isSince3_5: Boolean = version >= LatestScalaVersions.Scala_3_5

  private def byVersion(before3_5: String, since3_5: String): String =
    if (isSince3_5) since3_5 else before3_5

  private val definitions =
    """trait Bar
      |trait IO
      |
      |def inter[S, S2]: List[S & S2] = ???
      |def union[S, S2]: List[S | S2] = ???
      |
      |val intersection: IO & (IO & Bar) = ???
      |""".stripMargin

  private def doDefinitionTest(definition: String, expected: String, selected: String = "x"): Unit = doTest(
    s"""$definitions
       |$definition
       |val selected = $START$selected$END
       |//$expected
       |""".stripMargin
  )

  def testIntersectionInTypeArgument(): Unit = doDefinitionTest(
    """trait <[A, B]
      |def foo[S, B, S2]: B < (S & S2) = ???
      |val x = foo[IO, String, IO & Bar]
      |""".stripMargin,
    "String < (IO & Bar)"
  )

  def testDeclaredTypeIsKept(): Unit = doDefinitionTest("", "IO & (IO & Bar)", selected = "intersection")

  def testVal(): Unit = doDefinitionTest("val x = intersection", "IO & Bar")

  def testVar(): Unit = doDefinitionTest("var x = intersection", "IO & Bar")

  def testDef(): Unit = doDefinitionTest("def x = intersection", "IO & Bar")

  def testMemberAsSeenFrom(): Unit = doDefinitionTest(
    """class C[S] { def m: List[S & IO] = ??? }
      |val x = new C[IO & Bar].m
      |""".stripMargin,
    "List[Bar & IO]"
  )

  def testTypeAliasIsKept(): Unit = doDefinitionTest(
    """type X[A] = A & IO
      |val alias: X[IO & Bar] = ???
      |val x = alias
      |""".stripMargin,
    "X[IO & Bar]"
  )

  def testIntersectionWithNothing(): Unit = doDefinitionTest("val x = inter[Nothing, IO]", "List[Nothing]")

  def testIntersectionWithAny(): Unit = doDefinitionTest("val x = inter[Any, IO]", "List[IO]")

  def testUnionWithNothing(): Unit = doDefinitionTest("val x = union[Nothing, IO]", "List[IO]")

  def testUnionWithNull(): Unit = doDefinitionTest("val x = union[Null, IO]", "List[IO]")

  def testUnionWithAny(): Unit = doDefinitionTest(
    "val x = union[Any, IO]",
    byVersion("List[Any | IO]", "List[Any]")
  )

  def testUnionWithSubsumedOperand(): Unit = doDefinitionTest(
    "val x = union[IO, IO | Bar]",
    byVersion("List[IO | (IO | Bar)]", "List[IO | Bar]")
  )

  def testIntersectionInUnion(): Unit = doDefinitionTest(
    "val x = union[IO, IO & (IO & Bar)]",
    byVersion("List[IO | IO & Bar]", "List[IO]")
  )

  def testOpaqueTypeOutsideOfScope(): Unit = doDefinitionTest(
    """object O {
      |  opaque type T = IO
      |}
      |val x = inter[O.T, IO]
      |""".stripMargin,
    "List[O.T & IO]"
  )

  def testOpaqueTypeInScope(): Unit = doDefinitionTest(
    """object O {
      |  opaque type T = IO
      |  val x = inter[T, IO]
      |}
      |""".stripMargin,
    "List[IO]",
    selected = "O.x"
  )
}

class Scala3AndOrTypesSimplificationTest_Scala_3_3 extends Scala3AndOrTypesSimplificationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3
}

class Scala3AndOrTypesSimplificationTest_Scala_3_4 extends Scala3AndOrTypesSimplificationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_4
}

class Scala3AndOrTypesSimplificationTest_Scala_3_5 extends Scala3AndOrTypesSimplificationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_5
}

class Scala3AndOrTypesSimplificationTest_Scala_3_10 extends Scala3AndOrTypesSimplificationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_10
}
