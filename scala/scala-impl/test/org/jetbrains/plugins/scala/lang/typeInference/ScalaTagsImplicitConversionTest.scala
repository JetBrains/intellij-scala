package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaTagsImplicitConversionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("com.lihaoyi" %% "scalatags" % "0.8.6")

  def testSCL17374(): Unit = checkTextHasNoErrors(
    """
      |import scalatags.Text.all._
      |
      |val test = div(
      |   div(),
      |   Some(div())
      | )
      |""".stripMargin
  )
}
