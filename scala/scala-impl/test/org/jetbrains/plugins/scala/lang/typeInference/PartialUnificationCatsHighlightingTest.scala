package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.project._
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class PartialUnificationCatsHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override implicit val version: ScalaVersion = Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("org.typelevel" %% "cats-core" % "1.4.0")

  override def setUp(): Unit = {
    super.setUp()
    getModule.scalaCompilerSettings.additionalCompilerOptions = Seq("-Ypartial-unification")
  }

  def testEitherSequence(): Unit = checkTextHasNoErrors(
    """
      |import cats.implicits._
      |
      |val x: Either[String, Option[Either[String, Int]]] = Right(Some(Right(1)))
      |val y = x.flatMap(_.sequence)
      |val z = x.flatMap(_.traverse(identity))
    """.stripMargin
  )

  def testSCL14919(): Unit = checkTextHasNoErrors(
    """
      |import cats._
      |import cats.implicits._
      |object A {
      |  val m: Map[String, Option[Int]] = Map(
      |    "1" -> 1.some,
      |    "2" -> 2.some
      |  )
      |  m.unorderedSequence
      |}
    """.stripMargin
  )
}
