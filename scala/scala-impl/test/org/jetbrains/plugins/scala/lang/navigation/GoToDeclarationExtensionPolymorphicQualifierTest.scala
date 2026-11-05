package org.jetbrains.plugins.scala.lang.navigation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.typeInference.Scala3ExtensionPolymorphicQualifierTest.withClassState
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class GoToDeclarationExtensionPolymorphicQualifierTest extends GoToDeclarationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_9

  // SCL-22528
  def testExtensionCall(): Unit = doTest(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  get.flat${CARET}Map(s => set(f(s)))
         |}
         |""".stripMargin
    ),
    expected = (is[ScFunction], "flatMap")
  )

  // SCL-22528
  def testFirstGeneratorNavigatesToFlatMap(): Unit = doTest(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  for {
         |    s <$CARET- get
         |    _ <- set(f(s))
         |  } yield ()
         |}
         |""".stripMargin
    ),
    expected = (is[ScFunction], "flatMap")
  )

  // SCL-22528
  def testLastGeneratorNavigatesToMap(): Unit = doTest(
    withClassState(
      s"""def modify[S](f: S => S): State[S, Unit] = {
         |  for {
         |    s <- get
         |    _ <$CARET- set(f(s))
         |  } yield ()
         |}
         |""".stripMargin
    ),
    expected = (is[ScFunction], "map")
  )
}

class GoToDeclarationExtensionPolymorphicQualifierTest_LTS extends GoToDeclarationExtensionPolymorphicQualifierTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_3
}
