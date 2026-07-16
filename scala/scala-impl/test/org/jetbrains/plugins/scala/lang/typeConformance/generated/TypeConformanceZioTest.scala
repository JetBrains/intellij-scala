package org.jetbrains.plugins.scala.lang.typeConformance.generated

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel


class TypeConformanceZioTest extends TypeConformanceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.languageLevel == ScalaLanguageLevel.Scala_2_13

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("dev.zio" %% "zio" % "1.0.0-RC18-2"))

  def testSCL17210(): Unit = {
    doTest(
      s"""import zio.{Has, ZLayer}
         |import zio.console.Console
         |import zio.random.Random
         |
         |type Example = Has[Example.Service]
         |object Example {
         |  trait Service
         |}
         |
         |val live: ZLayer[Console with Random, Nothing, Example] =
         |  ZLayer.fromServices[Console.Service, Random.Service, Example.Service] { (console, random) =>
         |    new Example.Service {}
         |  }
         |//true
      """.stripMargin)
  }

  def testSCL17210_differentOrder(): Unit = {
    doTest(
      s"""import zio.{Has, ZLayer}
         |import zio.console.Console
         |import zio.random.Random
         |
         |type Example = Has[Example.Service]
         |object Example {
         |  trait Service
         |}
         |
         |val live: ZLayer[Random with Console, Nothing, Example] =
         |  ZLayer.fromServices[Console.Service, Random.Service, Example.Service] { (console, random) =>
         |    new Example.Service {}
         |  }
         |//true
      """.stripMargin)
  }

}

/**
 * SCL-22562: with ZIO 2's `ZIO.foreach[Collection[+X] <: Iterable[X]]`, the collection
 * type parameter should be inferred from a base type that reflects Scala's linearization
 * order (later mixins take precedence).
 * Example: `Range extends AbstractSeq[Int] with IndexedSeq[Int]` — inference must pick
 * `IndexedSeq`, not `AbstractSeq`, otherwise the implicit `BuildFrom` search fails.
 */
class TypeConformanceZio2Test extends TypeConformanceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("dev.zio" %% "zio" % "2.1.26"))

  def testSCL22562(): Unit = {
    checkTextHasNoErrors(
      """
        |import zio.*
        |
        |object MainApp extends ZIOAppDefault {
        |  def run =
        |    for
        |      _    <- ZIO.foreach(Range(1, 5))(i => ZIO.succeed(i))
        |    yield ()
        |}
        |
        |""".stripMargin
    )
  }

  def testSCL22562_option(): Unit = {
    checkTextHasNoErrors(
      s"""
         |import zio.*
         |
         |object MainApp extends ZIOAppDefault {
         |  def run =
         |    for
         |      _    <- ZIO.foreach(Some("test"))(ZIO.succeed(_)).someOrElse("other test")
         |    yield ()
         |}
      """.stripMargin)
  }
}
