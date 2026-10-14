package org.jetbrains.plugins.scala.annotator.withLibraryDependencies

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class CatsEffectCpsHighlightingTest_Scala3 extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader(
      ("org.typelevel" %% "cats-effect-cps" % "0.4.0").transitive(),
      ("org.typelevel" %% "cats-effect" % "3.4.1").transitive()
    )

  def testAwait(): Unit = assertNoErrors(
    """import cats.effect.cps.*
      |import cats.effect.{IO, IOApp}
      |
      |object AsyncAwait extends IOApp.Simple {
      |  case class A(x: Int)
      |  def run: IO[Unit] = async[IO] {
      |    println(IO(A(1)).await.x)
      |  }
      |}
      |""".stripMargin
  )

  def testAwaitExplicitUsing(): Unit = assertNoErrors(
    """import cats.effect.cps.*
      |import cats.effect.{IO, IOApp}
      |
      |object AsyncAwait extends IOApp.Simple {
      |  case class A(x: Int)
      |  def run: IO[Unit] = async[IO](using cats.effect.cps.catsEffectCpsConcurrentMonad) {
      |    println(IO(A(1)).await.x)
      |  }
      |}
      |""".stripMargin
  )
}
