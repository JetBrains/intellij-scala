package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class KyoHighlightingTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_LTS

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader(
        ("io.getkyo" %% "kyo-core" % "0.16.2").transitive(),
        ("io.getkyo" %% "kyo-prelude" % "0.16.2").transitive()
      )

  def testSCL23717(): Unit = checkTextHasNoErrors(
    """
      |import kyo.*
      |
      |object TestKyo {
      |  val program =
      |    for {
      |      a <- Var.get[String]
      |      b <- Env.get[Int]
      |    } yield (a, b)
      |}
      |""".stripMargin
  )
}
